package com.jrobertgardzinski.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of the {@code outbox_events} table: an event written in the same transaction as the state
 * change that caused it, waiting for the publisher to hand it to Kafka ({@code publishedAt} set).
 */
@MappedEntity("outbox_events")
record OutboxEventEntity(
        @Id UUID id,
        String topic,
        String eventKey,
        String payload,
        Instant createdAt,
        @Nullable Instant publishedAt,
        // the correlation id of the request that wrote this event — forwarded as a Kafka header at
        // drain time so the async consumer logs the same cid; null for events written outside a request
        @Nullable String cid) {
}
