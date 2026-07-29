package com.jrobertgardzinski;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Identity as the CONSUMER of the portal's single outcome: these pacts state the exact shape of
 * the {@code offboarding-events} announcements this service acts on — proven by driving the real
 * listener with the pact's payload. The generated pact (../pacts, committed) is verified against
 * the portal orchestrator's REAL outcome-building code by its provider test. Only the fields this
 * listener reads are in the contract; the producer may add more.
 *
 * <p>The contract gained {@code id} on 2026-07-29. offboarding has always derived it from
 * (saga, type) so a re-announcement is byte-identical — "consumers deduplicate on the id" says its
 * own comment — but this consumer read only the e-mail, and an e-mail is a person rather than a
 * run. Now that the id decides whether an outcome has already been acted on, security genuinely
 * DEPENDS on it, and a dependency a consumer relies on belongs in the pact where the producer's
 * build can break on it.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "microservice-offboarding", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class OffboardingOutcomeContractTest {

    private final AccountDeletionOrchestrator orchestrator = mock(AccountDeletionOrchestrator.class);

    /** Remembers what it was handed, so a re-announcement is recognisable — the real thing is a table. */
    private final java.util.Set<String> alreadyActedOn = new java.util.HashSet<>();

    private final OffboardingOutcomeListener listener = new OffboardingOutcomeListener(
            orchestrator,
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            },
            JsonMapper.createDefault(),
            (outcomeId, outcomeType, at) -> alreadyActedOn.add(outcomeId),
            java.time.Clock.fixed(java.time.Instant.parse("2026-07-29T00:00:00Z"),
                    java.time.ZoneOffset.UTC));

    @Pact(consumer = "microservice-security")
    MessagePact portalPurged(MessagePactBuilder builder) {
        return builder.expectsToReceive("a portal content purged announcement")
                .withContent(new PactDslJsonBody()
                        .uuid("id", "6b1f0e6a-2c1e-4f1a-9a3d-0f5a8c2b7e11")
                        .stringValue("type", "PORTAL_CONTENT_PURGED")
                        .stringType("email", "leaver@example.com"))
                .toPact();
    }

    @Pact(consumer = "microservice-security")
    MessagePact portalPurgeFailed(MessagePactBuilder builder) {
        return builder.expectsToReceive("a portal purge failed announcement")
                .withContent(new PactDslJsonBody()
                        .uuid("id", "9d2c4b8e-7a10-4c33-8e21-3b6f5d9a0c42")
                        .stringValue("type", "PORTAL_PURGE_FAILED")
                        .stringType("email", "leaver@example.com"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "portalPurged")
    void thePurgedAnnouncementFinishesTheDeletion(List<Message> messages) {
        listener.handle(messages.get(0).contentsAsString());
        verify(orchestrator).completePurge("leaver@example.com");
    }

    @Test
    @PactTestFor(pactMethod = "portalPurgeFailed")
    void theFailureAnnouncementRollsTheDeletionBack(List<Message> messages) {
        listener.handle(messages.get(0).contentsAsString());
        verify(orchestrator).compensate("leaver@example.com", java.util.List.of());
    }

    @Test
    @PactTestFor(pactMethod = "portalPurged")
    void theSameAnnouncementTwiceIsActedOnOnce(List<Message> messages) {
        // The sweeper re-publishes an outcome whose first announcement never got its outbox mark,
        // and it does so byte-identically. Acting on it twice is what let a stale outcome close a
        // NEWER saga for the same person: the account unlocked while the portal was still erasing.
        listener.handle(messages.get(0).contentsAsString());
        listener.handle(messages.get(0).contentsAsString());

        verify(orchestrator, org.mockito.Mockito.times(1)).completePurge("leaver@example.com");
    }
}
