package com.jrobertgardzinski.persistence;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.messaging.annotation.MessageHeader;
import io.micronaut.scheduling.annotation.Scheduled;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;

/**
 * Drains the transactional outbox to Kafka: unpublished events go out in creation order and are
 * then marked published. A crash between send and mark redelivers the event — at-least-once by
 * design; consumers deduplicate by the event id inside the payload. Off in the {@code test}
 * environment (no broker there).
 */
@Singleton
@Requires(notEnv = "test")
@Requires(beans = DataSource.class)
class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);

    @KafkaClient
    @Requires(notEnv = "test")
    interface EventsProducer {
        void send(@Topic String topic, @KafkaKey String key,
                  @MessageHeader("X-Correlation-Id") @Nullable String cid, String payload);
    }

    private final OutboxEventJdbcRepository outbox;
    private final EventsProducer producer;
    private final Clock clock;

    OutboxPublisher(OutboxEventJdbcRepository outbox, EventsProducer producer, Clock clock) {
        this.outbox = outbox;
        this.producer = producer;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "1s", initialDelay = "5s")
    void drain() {
        for (OutboxEventEntity event : outbox.findByPublishedAtIsNullOrderByCreatedAt()) {
            try {
                if (event.cid() != null) {
                    MDC.put("cid", event.cid());   // the drain log carries the originating request's cid
                }
                // re-establish the originating request's span as the parent, so the agent injects a
                // traceparent that continues that trace instead of starting a disconnected one
                try (Scope ignored = parentScope(event.traceparent())) {
                    producer.send(event.topic(), event.eventKey(), event.cid(), event.payload());
                }
                outbox.markPublished(event.id(), Instant.now(clock));
            } catch (RuntimeException brokerDown) {
                LOG.warn("outbox drain interrupted (will retry): {}", brokerDown.getMessage());
                return; // keep ordering: stop at the first failure, retry next tick
            } finally {
                MDC.remove("cid");
            }
        }
    }

    /** The stored traceparent as a current OTel scope (its span becomes the parent), or a no-op. */
    private static Scope parentScope(String traceparent) {
        if (traceparent == null) {
            return Scope.noop();
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {
            return Scope.noop();
        }
        SpanContext parent = SpanContext.createFromRemoteParent(
                parts[1], parts[2], TraceFlags.fromHex(parts[3], 0), TraceState.getDefault());
        return parent.isValid() ? Context.root().with(Span.wrap(parent)).makeCurrent() : Scope.noop();
    }
}
