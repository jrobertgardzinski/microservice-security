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
 * The account-deletion saga, orchestrated: {@link #begin} locks the account (saga STARTED) and
 * asks microservice-memes — through the outbox, so the request commits with the lock — to purge
 * the user's content (their memes with whole comment threads, their comments elsewhere
 * anonymised, their votes gone). The memes confirmation drives {@link #completePurge}: the user
 * is deleted for good and a goodbye mail goes out. {@link #compensateOverdue} is the timeout
 * path: no confirmation in time unlocks the account and mails an apology. All transitions are
 * idempotent — at-least-once delivery makes duplicates a fact of life.
 */
@Singleton
public class AccountDeletionOrchestrator implements AccountDeletionSaga {

    static final String COMMANDS_TOPIC = "content-commands";
    static final String MAIL_TOPIC = "mail-requests";

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionOrchestrator.class);

    private final AccountDeletionSagaStore sagas;
    private final OutboxAppender outbox;
    private final DeleteAccount deleteAccount;
    private final UserRepository userRepository;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration purgeTimeout;

    AccountDeletionOrchestrator(AccountDeletionSagaStore sagas, OutboxAppender outbox,
                                DeleteAccount deleteAccount, UserRepository userRepository,
                                JsonMapper json, Clock clock,
                                @Value("${account-deletion.purge-timeout:2m}") Duration purgeTimeout) {
        this.sagas = sagas;
        this.outbox = outbox;
        this.deleteAccount = deleteAccount;
        this.userRepository = userRepository;
        this.json = json;
        this.clock = clock;
        this.purgeTimeout = purgeTimeout;
    }

    @Override
    public void begin(Email email, PurgeChoices purgeChoices) {
        UUID sagaId = UUID.randomUUID();
        sagas.start(sagaId, email.value(), Instant.now(clock));
        Map<String, Object> command = new LinkedHashMap<>(Map.of(
                "id", UUID.randomUUID().toString(),
                "sagaId", sagaId.toString(),
                "type", "PURGE_USER_CONTENT",
                "email", email.value()));
        if (purgeChoices.memesRule().isPresent() && purgeChoices.commentsRule().isPresent()) {
            command.put("policy", Map.of(
                    "memes", purgeChoices.memesRule().get(),
                    "comments", purgeChoices.commentsRule().get()));
        }
        outbox.append(COMMANDS_TOPIC, email.value(), write(command));
    }

    /**
     * One content service ("memes" or "comments") confirmed its purge; the deletion finishes only
     * when the LAST missing confirmation arrives. Duplicates and strays are no-ops.
     */
    public void completePurge(String email, String participant) {
        if (!sagas.confirm(email, participant, Instant.now(clock))) {
            LOG.info("recorded {} purge confirmation for {}; saga not complete yet", participant, email);
            return;
        }
        deleteAccount.execute(Email.of(email));
        appendMail("ACCOUNT_DELETED", email);
        LOG.info("account deletion completed for {}", email);
    }

    /** The timeout path: sagas without confirmation get rolled back and the user apologised to. */
    public void compensateOverdue() {
        Instant now = Instant.now(clock);
        for (String email : sagas.compensateOverdue(now.minus(purgeTimeout), now)) {
            userRepository.clearPendingDeletion(Email.of(email));
            appendMail("ACCOUNT_DELETION_FAILED", email);
            LOG.warn("account deletion compensated (no purge confirmation in {}) for {}", purgeTimeout, email);
        }
    }

    private void appendMail(String type, String to) {
        outbox.append(MAIL_TOPIC, to, write(Map.<String, Object>of(
                "id", UUID.randomUUID().toString(), "type", type, "to", to)));
    }

    private String write(Map<String, ?> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
