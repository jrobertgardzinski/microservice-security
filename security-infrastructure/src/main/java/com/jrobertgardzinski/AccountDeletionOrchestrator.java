package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.persistence.AccountDeletionSagaStore;
import com.jrobertgardzinski.persistence.OutboxAppender;
import com.jrobertgardzinski.security.domain.port.AccountDeletionSaga;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.PurgeChoices;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Identity's side of the account-deletion saga — the ORCHESTRATION itself lives in the portal
 * ({@code microservice-offboarding}), because the content being purged is the portal's domain.
 * {@link #begin} locks the account (saga STARTED) and announces the FACT that deletion was
 * requested — through the outbox, so the fact commits with the lock; the fact ferries the
 * leaver's purge choices without knowing their vocabulary. The portal answers with ONE outcome:
 * {@link #completePurge} (the user is deleted for good, a goodbye mail goes out) or
 * {@link #compensate} (the account unlocks, an apology goes out). {@link #compensateOverdue} is
 * the safety net for a dead orchestrator: no outcome at all in time unlocks the account too.
 * All transitions are idempotent — at-least-once delivery makes duplicates a fact of life.
 *
 * <p>An identity-only deployment (no portal, e.g. security + the F1 game) sets
 * {@code account-deletion.await-portal-purge=false}: there is no content to purge anywhere, so
 * the account deletes immediately.
 */
@Singleton
public class AccountDeletionOrchestrator implements AccountDeletionSaga {

    static final String FACTS_TOPIC = "security-events";
    static final String MAIL_TOPIC = "mail-requests";

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionOrchestrator.class);

    private final AccountDeletionSagaStore sagas;
    private final OutboxAppender outbox;
    private final DeleteAccount deleteAccount;
    private final UserRepository userRepository;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration purgeTimeout;
    private final boolean awaitPortalPurge;

    AccountDeletionOrchestrator(AccountDeletionSagaStore sagas, OutboxAppender outbox,
                                DeleteAccount deleteAccount, UserRepository userRepository,
                                JsonMapper json, Clock clock,
                                // the safety net fires well AFTER the portal's own timeout (2m),
                                // so the portal's failure announcement normally wins the race
                                @Value("${account-deletion.purge-timeout:5m}") Duration purgeTimeout,
                                @Value("${account-deletion.await-portal-purge:true}") boolean awaitPortalPurge) {
        this.sagas = sagas;
        this.outbox = outbox;
        this.deleteAccount = deleteAccount;
        this.userRepository = userRepository;
        this.json = json;
        this.clock = clock;
        this.purgeTimeout = purgeTimeout;
        this.awaitPortalPurge = awaitPortalPurge;
    }

    @Override
    public void begin(Email email, PurgeChoices purgeChoices) {
        if (!awaitPortalPurge) {
            // identity-only deployment: no portal, no content, nothing to wait for
            deleteAccount.execute(email);
            appendMail("ACCOUNT_DELETED", email.value());
            LOG.info("account deleted immediately (no portal configured) for {}", email.value());
            return;
        }
        UUID sagaId = UUID.randomUUID();
        sagas.start(sagaId, email.value(), Instant.now(clock));
        Map<String, Object> fact = new LinkedHashMap<>(Map.of(
                "id", UUID.randomUUID().toString(),
                "sagaId", sagaId.toString(),
                "type", "ACCOUNT_DELETION_REQUESTED",
                "email", email.value(),
                "version", 1));
        if (!purgeChoices.rules().isEmpty()) {
            fact.put("policy", purgeChoices.rules());
        }
        outbox.append(FACTS_TOPIC, email.value(), write(fact));
    }

    /**
     * The portal announced its content purged (PORTAL_CONTENT_PURGED): the user is deleted for
     * good and a goodbye mail goes out. Duplicates and strays are no-ops — the store's
     * STARTED→COMPLETED latch admits exactly one caller.
     */
    public void completePurge(String email) {
        if (!sagas.complete(email, Instant.now(clock))) {
            LOG.info("portal-purged outcome for {} matched no running deletion; ignoring", email);
            return;
        }
        deleteAccount.execute(Email.of(email));
        appendMail("ACCOUNT_DELETED", email);
        LOG.info("account deletion completed for {}", email);
    }

    /** The portal announced the purge FAILED: the account unlocks and the user is apologised to. */
    public void compensate(String email) {
        if (!sagas.compensate(email, Instant.now(clock))) {
            LOG.info("purge-failed outcome for {} matched no running deletion; ignoring", email);
            return;
        }
        userRepository.clearPendingDeletion(Email.of(email));
        appendMail("ACCOUNT_DELETION_FAILED", email);
        LOG.warn("account deletion compensated (portal reported a failed purge) for {}", email);
    }

    /** The safety net: no outcome AT ALL in time (a dead orchestrator) unlocks the account too. */
    public void compensateOverdue() {
        Instant now = Instant.now(clock);
        for (String email : sagas.compensateOverdue(now.minus(purgeTimeout), now)) {
            userRepository.clearPendingDeletion(Email.of(email));
            appendMail("ACCOUNT_DELETION_FAILED", email);
            LOG.warn("account deletion compensated (no portal outcome in {}) for {}", purgeTimeout, email);
        }
    }

    private void appendMail(String type, String to) {
        outbox.append(MAIL_TOPIC, to, write(Map.<String, Object>of(
                "id", UUID.randomUUID().toString(), "type", type, "to", to, "version", 1)));
    }

    private String write(Map<String, ?> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
