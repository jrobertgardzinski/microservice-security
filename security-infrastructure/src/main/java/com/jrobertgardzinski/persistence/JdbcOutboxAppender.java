package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.MDC;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** The real {@link OutboxAppender}: a row in {@code outbox_events}, drained by {@link OutboxPublisher}. */
@Singleton
@Requires(beans = DataSource.class)
class JdbcOutboxAppender implements OutboxAppender {

    private final OutboxEventJdbcRepository repository;
    private final Clock clock;

    JdbcOutboxAppender(OutboxEventJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void append(String topic, String key, String payload) {
        // capture the request's correlation id (if any) so the event carries it across the broker
        repository.save(new OutboxEventEntity(
                UUID.randomUUID(), topic, key, payload, Instant.now(clock), null, MDC.get("cid")));
    }
}
