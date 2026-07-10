package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.persistence.OutboxAppender;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.port.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.vo.token.AbstractToken;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

/**
 * Production notifiers (outside the {@code test} environment): a mail request becomes an event in
 * the transactional outbox — same transaction as the state change — and reaches microservice-email
 * through Kafka ({@code mail-requests} topic). Replaces the earlier synchronous HTTP client: a
 * mail-service outage no longer fails registration or password reset; the event just waits.
 */
@Factory
@Requires(notEnv = "test")
@Requires(beans = DataSource.class)
class OutboxMailNotifiers {

    static final String TOPIC = "mail-requests";

    private final OutboxAppender outbox;
    private final JsonMapper json;

    OutboxMailNotifiers(OutboxAppender outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Singleton
    EmailVerificationNotifier emailVerificationNotifier(
            @Value("${email-service.verify-link-base}") String verifyLinkBase) {
        return (email, token) -> append("VERIFICATION", email, verifyLinkBase, token);
    }

    @Singleton
    PasswordResetNotifier passwordResetNotifier(
            @Value("${email-service.reset-link-base}") String resetLinkBase) {
        return (email, token) -> append("PASSWORD_RESET", email, resetLinkBase, token);
    }

    @Singleton
    RegistrationNoticeNotifier registrationNoticeNotifier() {
        return email -> append(Map.of(
                "id", UUID.randomUUID().toString(),
                "type", "ALREADY_REGISTERED",
                "to", email.value(),
                "version", 1));
    }

    private void append(String type, Email email, String linkBase, AbstractToken token) {
        append(Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "to", email.value(),
                "link", linkBase + token.value(),
                "version", 1));
    }

    // "version" is the envelope's escape hatch for a breaking change (ADR 0004 in the workspace):
    // within version 1 fields are only ever ADDED and consumers ignore what they don't know
    private void append(Map<String, ?> event) {
        try {
            outbox.append(TOPIC, (String) event.get("to"), json.writeValueAsString(event));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
