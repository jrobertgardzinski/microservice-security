package com.jrobertgardzinski;

import au.com.dius.pact.provider.PactVerifyProvider;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.persistence.AccountDeletionSagaStore;
import com.jrobertgardzinski.persistence.OutboxAppender;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.PurgeChoices;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import io.micronaut.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The producer's half of the cross-service contracts: every event shape a consumer's pact relies
 * on, produced by the REAL producer code — the outbox notifiers and the account-deletion
 * orchestrator, not hand-written JSON. The provider tests (one per consumer pact folder) find
 * these methods by their {@link PactVerifyProvider} description and match the returned payload
 * against the consumer's expectations. If a producer ever renames or drops a field a consumer
 * reads, the verification here goes red — in THIS repo's build, not in a live stack.
 */
public class SecurityEventPacts {

    private static final JsonMapper JSON = JsonMapper.createDefault();

    // --- mail requests, as microservice-email consumes them ------------------------------------

    @PactVerifyProvider("a verification mail request")
    public String aVerificationMailRequest() {
        CapturingOutbox outbox = new CapturingOutbox();
        new OutboxMailNotifiers(outbox, JSON)
                .emailVerificationNotifier("http://localhost:8083/?verify=")
                .sendVerificationLink(Email.of("user@example.com"), VerificationToken.random());
        return outbox.only("mail-requests");
    }

    @PactVerifyProvider("a password reset mail request")
    public String aPasswordResetMailRequest() {
        CapturingOutbox outbox = new CapturingOutbox();
        new OutboxMailNotifiers(outbox, JSON)
                .passwordResetNotifier("http://localhost:8080/reset-password?token=")
                .sendResetLink(Email.of("user@example.com"), PasswordResetToken.random());
        return outbox.only("mail-requests");
    }

    @PactVerifyProvider("an already-registered notice mail request")
    public String anAlreadyRegisteredNoticeMailRequest() {
        CapturingOutbox outbox = new CapturingOutbox();
        new OutboxMailNotifiers(outbox, JSON)
                .registrationNoticeNotifier()
                .sendAlreadyRegistered(Email.of("owner@example.com"));
        return outbox.only("mail-requests");
    }

    @PactVerifyProvider("an auth code mail request")
    public String anAuthCodeMailRequest() {
        CapturingOutbox outbox = new CapturingOutbox();
        new OutboxEmailCodeChannel(outbox, JSON).sendCode("signin@example.com", "482913");
        return outbox.only("mail-requests");
    }

    @PactVerifyProvider("an account deleted mail request")
    public String anAccountDeletedMailRequest() {
        CapturingOutbox outbox = new CapturingOutbox();
        orchestrator(outbox).completePurge("leaver@example.com", "memes");
        return outbox.only("mail-requests");
    }

    @PactVerifyProvider("an account deletion failed mail request")
    public String anAccountDeletionFailedMailRequest() {
        CapturingOutbox outbox = new CapturingOutbox();
        orchestrator(outbox).compensateOverdue();
        return outbox.only("mail-requests");
    }

    // --- saga commands, as memes / comments / user-collections consume them --------------------

    @PactVerifyProvider("a purge user content command")
    public String aPurgeUserContentCommand() {
        CapturingOutbox outbox = new CapturingOutbox();
        orchestrator(outbox).begin(Email.of("leaver@example.com"), PurgeChoices.serviceDefaults());
        return outbox.only("content-commands");
    }

    @PactVerifyProvider("a purge user content command with an explicit policy")
    public String aPurgeUserContentCommandWithAnExplicitPolicy() {
        CapturingOutbox outbox = new CapturingOutbox();
        orchestrator(outbox).begin(Email.of("leaver@example.com"),
                new PurgeChoices(Optional.of("DELETE"), Optional.of("ANONYMIZE_AUTHOR")));
        return outbox.only("content-commands");
    }

    /** The real orchestrator over a stubbed saga store: confirmations complete, timeouts expire. */
    private static AccountDeletionOrchestrator orchestrator(OutboxAppender outbox) {
        AccountDeletionSagaStore sagas = mock(AccountDeletionSagaStore.class);
        when(sagas.confirm(any(), any(), any())).thenReturn(true);
        when(sagas.compensateOverdue(any(), any())).thenReturn(List.of("leaver@example.com"));
        return new AccountDeletionOrchestrator(sagas, outbox, mock(DeleteAccount.class),
                mock(UserRepository.class), JSON, Clock.systemUTC(), Duration.ofMinutes(2));
    }

    /** Captures what the producer appended; the payload on the expected topic IS the message. */
    private static final class CapturingOutbox implements OutboxAppender {

        private final List<String> topics = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();

        @Override
        public void append(String topic, String key, String payload) {
            topics.add(topic);
            payloads.add(payload);
        }

        String only(String topic) {
            assertEquals(List.of(topic), topics,
                    "expected exactly one event, on topic " + topic);
            return payloads.get(0);
        }
    }
}
