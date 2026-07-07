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
 * The saga's ears: purge confirmations arrive from microservice-memes on {@code memes-events}
 * (which also carries MEME_DELETED lifecycle events - ignored here), from microservice-comments on
 * {@code comments-events}, and from microservice-user-collections on {@code usercollections-events}.
 * Each USER_CONTENT_PURGED event records that participant's confirmation; the orchestrator completes
 * the deletion when all three are in.
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
    void fromMemes(String payload, @MessageHeader("X-Correlation-Id") @Nullable String cid) {
        confirmIfPurged(payload, "memes", cid);
    }

    @Topic("comments-events")
    void fromComments(String payload, @MessageHeader("X-Correlation-Id") @Nullable String cid) {
        confirmIfPurged(payload, "comments", cid);
    }

    @Topic("usercollections-events")
    void fromCollections(String payload, @MessageHeader("X-Correlation-Id") @Nullable String cid) {
        confirmIfPurged(payload, "collections", cid);
    }

    private void confirmIfPurged(String payload, String participant, String cid) {
        if (cid != null) {
            MDC.put("cid", cid);   // continue the trace the originating request started
        }
        try {
            handle(payload, participant);
        } finally {
            MDC.remove("cid");
        }
    }

    private void handle(String payload, String participant) {
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
