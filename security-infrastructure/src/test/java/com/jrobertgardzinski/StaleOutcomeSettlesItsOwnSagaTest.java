package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.persistence.AccountDeletionSagaStore;
import com.jrobertgardzinski.persistence.InMemoryOutboxAppender;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AccountClosure;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import io.micronaut.context.ApplicationContext;
import io.micronaut.json.JsonMapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * One person, two deletions, and the outcome of the one that is OVER.
 *
 * <p>The sequence needs nothing exotic. The portal takes longer than the safety net allows, the net
 * unlocks the account and apologises, the person asks to be deleted again — and only then does the
 * portal's confirmation of the FIRST case arrive. It is that case's first announcement, so nothing
 * deduplicates it away; and while the settle matched the address alone, it closed the deletion
 * running NOW: the account went for good on a confirmation about content purged for a case this
 * service had already given up on, while the portal was still purging for the newer one.
 *
 * <p>The real store is used rather than a double on purpose: WHICH saga an outcome settles is a
 * store semantic, and this is the situation the double has already drifted on once (see
 * {@code AccountDeletionSagaStoreContract}). Everything above it is the production path — the real
 * listener parsing the portal's payload, the real orchestrator acting on it.
 */
@Epic("Account deletion")
@Feature("Saga correlation")
class StaleOutcomeSettlesItsOwnSagaTest {

    private static final String EMAIL = "alice@example.com";

    /** A clock the test moves, because the safety net only fires once time has passed. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-12T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration by) { now = now.plus(by); }
    }

    private final MovableClock clock = new MovableClock();
    private final InMemoryOutboxAppender outbox = new InMemoryOutboxAppender();
    private final DeleteAccount deleteAccount = mock(DeleteAccount.class);

    @Test
    @DisplayName("the outcome of a given-up deletion leaves the person's newer one running")
    void a_stale_outcome_does_not_settle_the_newer_saga() throws Exception {
        try (ApplicationContext context = ApplicationContext.run("test")) {
            AccountDeletionSagaStore sagas = context.getBean(AccountDeletionSagaStore.class);
            AccountDeletionOrchestrator orchestrator = new AccountDeletionOrchestrator(
                    sagas, outbox, deleteAccount, mock(UserRepository.class),
                    JsonMapper.createDefault(), clock, Duration.ofMinutes(12), true);

            // alice asks to be deleted: saga A opens and the fact goes to the portal
            orchestrator.begin(AccountClosure.requestedByOwner(Email.of(EMAIL)));
            UUID sagaA = announcedSaga();

            // the portal says nothing in time, so the net unlocks the account and apologises
            clock.advance(Duration.ofMinutes(13));
            orchestrator.compensateOverdue();

            // alice asks again: saga B opens, and the portal is purging for it right now
            orchestrator.begin(AccountClosure.requestedByOwner(Email.of(EMAIL)));
            UUID sagaB = announcedSaga();
            assertNotEquals(sagaA, sagaB, "a second request opens a second saga");

            // ...and NOW saga A's confirmation arrives. First announcement, nothing to deduplicate.
            listener(orchestrator).handle(purged(sagaA));

            verify(deleteAccount, never()).execute(any());
            assertTrue(outbox.appended().stream()
                            .noneMatch(event -> event.payload().contains("\"type\":\"ACCOUNT_DELETED\"")),
                    "a goodbye mail went out on an outcome belonging to a closed case");
            assertTrue(sagas.complete(sagaB, EMAIL, clock.instant()),
                    "saga B must still be STARTED: settling it on saga A's outcome deletes the"
                            + " account while the portal is still purging for B, and B's own"
                            + " outcome then finds nothing left to settle");
        }
    }

    /** The real listener over the real orchestrator; the claim always succeeds — nothing repeats here. */
    private OffboardingOutcomeListener listener(AccountDeletionOrchestrator orchestrator) {
        return new OffboardingOutcomeListener(orchestrator,
                new TransactionBoundary() {
                    @Override
                    public <T> T execute(Supplier<T> work) {
                        return work.get();
                    }
                },
                JsonMapper.createDefault(),
                (outcomeId, outcomeType, at) -> true,
                clock);
    }

    /** The saga of the fact just announced — the handle the portal echoes back on its outcome. */
    private UUID announcedSaga() throws Exception {
        String fact = outbox.appended().stream()
                .filter(event -> event.topic().equals("security-events"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no deletion fact in the outbox"))
                .payload();
        return UUID.fromString(String.valueOf(
                JsonMapper.createDefault().readValue(fact, Map.class).get("sagaId")));
    }

    /** The portal's confirmation, shaped as microservice-offboarding writes it. */
    private static String purged(UUID saga) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"PORTAL_CONTENT_PURGED\",\"email\":\""
                + EMAIL + "\",\"sagaId\":\"" + saga + "\",\"version\":1}";
    }
}
