package com.jrobertgardzinski;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;
import io.micronaut.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The saga's ear: confirmations from microservice-memes arrive on {@code memes-events}; a
 * {@code USER_CONTENT_PURGED} event completes the account deletion. Idempotent by way of the
 * saga store — replays and duplicates fall through as no-ops.
 */
@KafkaListener(groupId = "security", offsetReset = OffsetReset.EARLIEST)
@Requires(notEnv = "test")
class MemesEventsListener {

    private static final Logger LOG = LoggerFactory.getLogger(MemesEventsListener.class);

    private final AccountDeletionOrchestrator orchestrator;
    private final TransactionBoundary transactionBoundary;
    private final JsonMapper json;

    MemesEventsListener(AccountDeletionOrchestrator orchestrator, TransactionBoundary transactionBoundary,
                        JsonMapper json) {
        this.orchestrator = orchestrator;
        this.transactionBoundary = transactionBoundary;
        this.json = json;
    }

    @Topic("memes-events")
    void receive(String payload) {
        Map<?, ?> event;
        try {
            event = json.readValue(payload, Map.class);
        } catch (Exception malformed) {
            LOG.warn("dropping malformed memes event: {}", payload);
            return;
        }
        if ("USER_CONTENT_PURGED".equals(event.get("type"))) {
            String email = String.valueOf(event.get("email"));
            transactionBoundary.execute(() -> {
                orchestrator.completePurge(email);
                return null;
            });
        }
    }
}
