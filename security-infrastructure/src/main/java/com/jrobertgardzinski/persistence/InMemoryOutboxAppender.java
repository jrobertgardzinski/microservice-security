package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Outbox stand-in for the database-less test environment: appended events are only remembered,
 * so tests can assert what WOULD have been published without a broker.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public class InMemoryOutboxAppender implements OutboxAppender {

    public record AppendedEvent(String topic, String key, String payload) {}

    private final List<AppendedEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(String topic, String key, String payload) {
        events.add(new AppendedEvent(topic, key, payload));
    }

    public List<AppendedEvent> appended() {
        return List.copyOf(events);
    }
}
