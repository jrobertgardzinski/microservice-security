package com.jrobertgardzinski;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import io.micronaut.messaging.annotation.MessageHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * The saga's ear, after the extraction: identity no longer hears three content participants —
 * the portal's orchestrator (microservice-offboarding) collects those and announces ONE outcome
 * on {@code offboarding-events}. PORTAL_CONTENT_PURGED finishes the deletion for good;
 * PORTAL_PURGE_FAILED rolls the lock back and apologises. Everything else on the topic is not
 * ours. Idempotent by way of the orchestrator's saga latch.
 */
@KafkaListener(groupId = "security", offsetReset = OffsetReset.EARLIEST)
@Requires(notEnv = "test")
class OffboardingOutcomeListener {

    private static final Logger LOG = LoggerFactory.getLogger(OffboardingOutcomeListener.class);

    private final AccountDeletionOrchestrator orchestrator;
    private final TransactionBoundary transactionBoundary;
    private final JsonMapper json;

    OffboardingOutcomeListener(AccountDeletionOrchestrator orchestrator, TransactionBoundary transactionBoundary,
                               JsonMapper json) {
        this.orchestrator = orchestrator;
        this.transactionBoundary = transactionBoundary;
        this.json = json;
    }

    @Topic("offboarding-events")
    void fromOffboarding(String payload, @MessageHeader("X-Correlation-Id") @Nullable String cid) {
        if (cid != null) {
            MDC.put("cid", cid);   // continue the trace the originating request started
        }
        try {
            handle(payload);
        } finally {
            MDC.remove("cid");
        }
    }

    /** Package-visible and broker-free, so the contract test drives the real consuming code. */
    void handle(String payload) {
        Map<?, ?> event;
        try {
            event = json.readValue(payload, Map.class);
        } catch (Exception malformed) {
            LOG.warn("dropping malformed offboarding event: {}", payload);
            return;
        }
        String type = String.valueOf(event.get("type"));
        if (!"PORTAL_CONTENT_PURGED".equals(type) && !"PORTAL_PURGE_FAILED".equals(type)) {
            return;
        }
        String email = String.valueOf(event.get("email"));
        transactionBoundary.execute(() -> {
            if ("PORTAL_CONTENT_PURGED".equals(type)) {
                orchestrator.completePurge(email);
            } else {
                orchestrator.compensate(email);
            }
            return null;
        });
    }
}
