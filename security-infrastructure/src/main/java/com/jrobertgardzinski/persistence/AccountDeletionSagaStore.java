package com.jrobertgardzinski.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence of the account-deletion saga's progress: STARTED when the purge is requested,
 * COMPLETED when the meme service confirms, COMPENSATED when the confirmation never came and the
 * account lock was rolled back. Transitions are idempotent — duplicated confirmations are normal
 * with at-least-once delivery.
 */
public interface AccountDeletionSagaStore {

    void start(UUID sagaId, String email, Instant at);

    /** STARTED → COMPLETED for this email; false when there is nothing to complete (duplicate). */
    boolean complete(String email, Instant at);

    /** STARTED older than the cutoff → COMPENSATED; returns the affected emails. */
    List<String> compensateOverdue(Instant cutoff, Instant at);
}
