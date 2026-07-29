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

    /** Two characters and the domain: enough to recognise a report, not enough to harvest. */
    private static String masked(String address) {
        int at = address.indexOf('@');
        return at <= 0 ? "***" : address.substring(0, Math.min(2, at)) + "***" + address.substring(at);
    }

    private final AccountDeletionOrchestrator orchestrator;
    private final TransactionBoundary transactionBoundary;
    private final JsonMapper json;
    private final com.jrobertgardzinski.persistence.ProcessedOutcomes processedOutcomes;
    private final java.time.Clock clock;

    OffboardingOutcomeListener(AccountDeletionOrchestrator orchestrator, TransactionBoundary transactionBoundary,
                               JsonMapper json,
                               com.jrobertgardzinski.persistence.ProcessedOutcomes processedOutcomes,
                               java.time.Clock clock) {
        this.orchestrator = orchestrator;
        this.transactionBoundary = transactionBoundary;
        this.json = json;
        this.processedOutcomes = processedOutcomes;
        this.clock = clock;
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
        String outcomeId = String.valueOf(event.get("id"));
        transactionBoundary.execute(() -> {
            // The id, not the e-mail, decides whether this outcome has already been acted on.
            // offboarding derives it from (saga, type) precisely so a re-announcement is
            // byte-identical — "consumers deduplicate on the id", says its own comment — and this
            // service, its only consumer, used to match on the e-mail instead. An e-mail is a
            // person; a person can have two deletion sagas. A re-announced outcome from the first
            // one then closed the second, unblocking an account while the portal was still erasing
            // its content, and the real outcome of the second saga was ignored for having no
            // STARTED saga left to close.
            if ("null".equals(outcomeId) || outcomeId.isBlank()) {
                // pre-ADR-0004 events and anything hand-published: fall through rather than drop,
                // but say so — an outcome without an id cannot be deduplicated by anyone
                LOG.warn("offboarding outcome {} for {} carries no id; acting on it without"
                        + " duplicate protection", type, masked(email));
            } else if (!processedOutcomes.claim(outcomeId, type, clock.instant())) {
                LOG.info("offboarding outcome {} ({}) was already acted on — ignoring the"
                        + " re-announcement", outcomeId, type);
                return null;
            }
            if ("PORTAL_CONTENT_PURGED".equals(type)) {
                orchestrator.completePurge(email);
            } else {
                orchestrator.compensate(email);
            }
            return null;
        });
    }
}
