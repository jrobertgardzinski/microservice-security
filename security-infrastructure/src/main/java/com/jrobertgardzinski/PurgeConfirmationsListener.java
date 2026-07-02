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
 * The saga's ears: purge confirmations arrive from microservice-memes on {@code memes-events}
 * (which also carries MEME_DELETED lifecycle events - ignored here) and from
 * microservice-comments on {@code comments-events}. Each USER_CONTENT_PURGED event records that
 * participant's confirmation; the orchestrator completes the deletion when both are in.
 */
@KafkaListener(groupId = "security", offsetReset = OffsetReset.EARLIEST)
@Requires(notEnv = "test")
class PurgeConfirmationsListener {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeConfirmationsListener.class);

    private final AccountDeletionOrchestrator orchestrator;
    private final TransactionBoundary transactionBoundary;
    private final JsonMapper json;

    PurgeConfirmationsListener(AccountDeletionOrchestrator orchestrator, TransactionBoundary transactionBoundary,
                               JsonMapper json) {
        this.orchestrator = orchestrator;
        this.transactionBoundary = transactionBoundary;
        this.json = json;
    }

    @Topic("memes-events")
    void fromMemes(String payload) {
        confirmIfPurged(payload, "memes");
    }

    @Topic("comments-events")
    void fromComments(String payload) {
        confirmIfPurged(payload, "comments");
    }

    private void confirmIfPurged(String payload, String participant) {
        Map<?, ?> event;
        try {
            event = json.readValue(payload, Map.class);
        } catch (Exception malformed) {
            LOG.warn("dropping malformed {} event: {}", participant, payload);
            return;
        }
        if ("USER_CONTENT_PURGED".equals(event.get("type"))) {
            String email = String.valueOf(event.get("email"));
            transactionBoundary.execute(() -> {
                orchestrator.completePurge(email, participant);
                return null;
            });
        }
    }
}
