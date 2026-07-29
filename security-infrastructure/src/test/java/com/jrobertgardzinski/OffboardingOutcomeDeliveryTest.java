package com.jrobertgardzinski;

import io.micronaut.configuration.kafka.annotation.ErrorStrategyValue;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The last announcement of an account deletion must not be droppable. Two halves, because the
 * guarantee needs both: the record has to come BACK when the work fails (the annotation), and the
 * work has to actually report that it failed (the code).
 *
 * <p>What this is guarding against is a shape of bug that leaves no trace anywhere. The portal
 * erases the user's content BEFORE it announces PORTAL_CONTENT_PURGED. Under the framework
 * defaults — no error strategy, {@code enable.auto.commit} — one exception out of the handler
 * (a rolled-back transaction on a momentary database hiccup) was logged, skipped and committed:
 * at-most-once delivery of the single message that finishes a deletion. The saga then sat STARTED
 * until the overdue sweeper unlocked the account and apologised, leaving the user with an empty
 * account, no ACCOUNT_DELETED, and no error line — the dedicated "CONTENT ERASED AFTER
 * COMPENSATION" log cannot fire, because from this side nothing appeared to go wrong.
 *
 * <p>Neither half is a style preference. Micronaut's retry strategies do nothing while offsets
 * auto-commit, so {@link OffsetStrategy#SYNC} is load-bearing, not decoration; and a handler that
 * swallows its own exception makes the retry strategy inert just as thoroughly as not declaring
 * one.
 */
class OffboardingOutcomeDeliveryTest {

    private static final KafkaListener LISTENER =
            OffboardingOutcomeListener.class.getAnnotation(KafkaListener.class);

    @Test
    @DisplayName("a failed outcome is redelivered rather than committed away")
    void the_outcome_listener_is_at_least_once() {
        assertNotEquals(OffsetStrategy.AUTO, LISTENER.offsetStrategy(),
                "auto-commit acknowledges a record the handler never got through, and it also"
                        + " makes the error strategy inert — micronaut only retries when it owns"
                        + " the offset");
        assertEquals(OffsetStrategy.SYNC, LISTENER.offsetStrategy(),
                "the offset must move only after the outcome is through");

        assertTrue(LISTENER.errorStrategy().value().isRetry(),
                "without a retrying error strategy an exception out of the handler is logged and"
                        + " skipped: at-most-once delivery of the message that finishes a deletion");
        assertEquals(ErrorStrategyValue.RETRY_EXPONENTIALLY_ON_ERROR, LISTENER.errorStrategy().value());
        assertTrue(LISTENER.errorStrategy().retryCount() >= 5,
                "the failure being ridden out is a database hiccup; one attempt a second later is"
                        + " not a retry policy");
        assertTrue(LISTENER.errorStrategy().stopOnExhaustedRetry(),
                "when the retries are spent the record must stay unread for a restart to replay —"
                        + " resuming at the next record is the silent loss this test exists for");
    }

    @Test
    @DisplayName("a failure inside the transaction escapes the handler, so the container sees it")
    void a_failed_transaction_is_not_swallowed() {
        OffboardingOutcomeListener listener = new OffboardingOutcomeListener(
                mock(AccountDeletionOrchestrator.class),
                new TransactionBoundary() {
                    @Override
                    public <T> T execute(Supplier<T> work) {
                        throw new IllegalStateException("could not get a connection");
                    }
                },
                JsonMapper.createDefault(),
                (outcomeId, outcomeType, at) -> true,
                java.time.Clock.systemUTC());

        // driven through the @Topic method, not handle(): the swallow this guards against can sit
        // anywhere between the broker and the work, and the entry point is where the container
        // either sees an exception or does not
        assertThrows(IllegalStateException.class, () -> listener.fromOffboarding(
                "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"PORTAL_CONTENT_PURGED\","
                        + "\"email\":\"leaver@example.com\"}", null),
                "the listener must let the failure out. Caught and logged here, the record is"
                        + " acknowledged as handled and the deletion is lost with the exception.");
    }
}
