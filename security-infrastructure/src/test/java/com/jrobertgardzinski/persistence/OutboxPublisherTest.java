package com.jrobertgardzinski.persistence;

import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drain must tell a PERMANENT send failure (this event will never publish) from a TRANSIENT one
 * (the broker is down). Only the permanent kind may be set aside; a transient failure stops the loop
 * and retries. Getting this wrong wedges the whole outbox on one poison row (poz. 14).
 */
class OutboxPublisherTest {

    @Test
    @DisplayName("a too-large or unserializable record is permanent — even wrapped")
    void permanentFailures() {
        assertTrue(OutboxPublisher.isPermanent(new RecordTooLargeException("2 MB payload")));
        assertTrue(OutboxPublisher.isPermanent(new SerializationException("bad value")));
        assertTrue(OutboxPublisher.isPermanent(
                new RuntimeException("wrapped", new RecordTooLargeException("too big"))));
    }

    @Test
    @DisplayName("a broker timeout or a plain runtime error is transient — the loop must retry, not skip")
    void transientFailures() {
        assertFalse(OutboxPublisher.isPermanent(new TimeoutException("broker unreachable")));
        assertFalse(OutboxPublisher.isPermanent(new RuntimeException("producer closed")));
    }
}
