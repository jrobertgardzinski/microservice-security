package com.jrobertgardzinski.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence of the account-deletion saga's progress: STARTED when the purges are requested;
 * each content service (memes, comments) confirms independently and the saga turns COMPLETED
 * only when every participant has; COMPENSATED when confirmations never came in time. Transitions
 * are idempotent — duplicated confirmations are normal with at-least-once delivery.
 */
public interface AccountDeletionSagaStore {

    void start(UUID sagaId, String email, Instant at);

    /**
     * Record one participant's confirmation ("memes" or "comments") for this email's running
     * saga; true only when this confirmation was the last missing one and the saga just
     * COMPLETED.
     */
    boolean confirm(String email, String participant, Instant at);

    /** STARTED older than the cutoff → COMPENSATED; returns the affected emails. */
    List<String> compensateOverdue(Instant cutoff, Instant at);
}
