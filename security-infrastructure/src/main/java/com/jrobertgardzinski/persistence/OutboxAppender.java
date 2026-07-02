package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Appends an event to the transactional outbox. Called inside a use case's transaction, so the
 * event commits or rolls back together with the state change that caused it.
 */
@Singleton
@Requires(beans = DataSource.class)
public class OutboxAppender {

    private final OutboxEventJdbcRepository repository;
    private final Clock clock;

    OutboxAppender(OutboxEventJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void append(String topic, String key, String payload) {
        repository.save(new OutboxEventEntity(
                UUID.randomUUID(), topic, key, payload, Instant.now(clock), null));
    }
}
