package com.jrobertgardzinski.persistence;

/**
 * Appends an event to the transactional outbox. Called inside a use case's transaction, so the
 * event commits or rolls back together with the state change that caused it. Backed by Postgres
 * when a datasource is present; an in-memory stand-in serves the broker-less test environment.
 */
public interface OutboxAppender {

    void append(String topic, String key, String payload);
}
