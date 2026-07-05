package com.jrobertgardzinski;

import com.jrobertgardzinski.persistence.OutboxAppender;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

/**
 * Production e-mail {@link CodeChannel} (outside the {@code test} environment): the one-time MFA
 * code becomes an {@code AUTH_CODE} event on the same transactional outbox that carries every other
 * mail — outbox → Kafka {@code mail-requests} → microservice-email. Same delivery guarantees, same
 * resilience: a mail-service hiccup does not fail the sign-in step, the event waits.
 */
@Singleton
@Requires(notEnv = "test")
@Requires(beans = DataSource.class)
final class OutboxEmailCodeChannel implements CodeChannel {

    static final String TOPIC = "mail-requests";

    private final OutboxAppender outbox;
    private final JsonMapper json;

    OutboxEmailCodeChannel(OutboxAppender outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public FactorType servesFactor() {
        return FactorType.EMAIL_CODE;
    }

    @Override
    public void sendCode(String target, String code) {
        try {
            outbox.append(TOPIC, target, json.writeValueAsString(Map.of(
                    "id", UUID.randomUUID().toString(),
                    "type", "AUTH_CODE",
                    "to", target,
                    "code", code)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
