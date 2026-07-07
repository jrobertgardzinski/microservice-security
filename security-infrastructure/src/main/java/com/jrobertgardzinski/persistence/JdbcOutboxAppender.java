package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.context.ServerRequestContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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
        // capture the request's correlation id (if any) so the event carries it across the broker.
        // Read it from the request attribute — Micronaut propagates the request across the @ExecuteOn
        // blocking thread the outbox is written on, whereas MDC (thread-local) does not; MDC is the
        // fallback for events appended outside a request (e.g. a scheduled job). Alongside it, the
        // active span's W3C traceparent, so the drain can continue this trace.
        repository.save(new OutboxEventEntity(
                UUID.randomUUID(), topic, key, payload, Instant.now(clock), null,
                correlationId(), traceparent()));
    }

    private static String correlationId() {
        return ServerRequestContext.currentRequest()
                .flatMap(request -> request.getAttribute("cid", String.class))
                .orElseGet(() -> MDC.get("cid"));
    }

    /** The current span as a W3C traceparent string, or null when there is no active span. */
    private static String traceparent() {
        SpanContext span = Span.current().getSpanContext();
        return span.isValid()
                ? "00-" + span.getTraceId() + "-" + span.getSpanId() + "-" + span.getTraceFlags().asHex()
                : null;
    }
}
