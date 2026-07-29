package com.jrobertgardzinski.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence of identity's side of the account-deletion saga: STARTED when the deletion fact is
 * announced to the portal; COMPLETED when the portal confirmed its content purged; COMPENSATED
 * when the portal reported a failed purge — or reported nothing at all in time. WHO purges is the
 * portal orchestrator's business now (microservice-offboarding); this store tracks only where
 * each deletion stands. Transitions are idempotent and latch once — at-least-once delivery makes
 * duplicates a fact of life.
 */
public interface AccountDeletionSagaStore {

    void start(UUID sagaId, String email, Instant at);

    /** STARTED → COMPLETED for this email's running saga; true only for the call that did it. */
    boolean complete(String email, Instant at);

    /** STARTED → COMPENSATED for this email's running saga; true only for the call that did it. */
    boolean compensate(String email, Instant at);

    /** STARTED older than the cutoff → COMPENSATED; returns the affected emails. */
    List<String> compensateOverdue(Instant cutoff, Instant at);

    /**
     * Whether this e-mail's most recent saga was COMPENSATED — i.e. this service gave up on the
     * deletion, unlocked the account and apologised.
     *
     * <p>Asked when a SUCCESS arrives that matches no running saga, because the two reasons for
     * that are worlds apart. A duplicate of an outcome already applied is routine. A genuine
     * purge confirmation arriving after we compensated means the portal erased the content ANYWAY:
     * the user keeps their account and their apology, and their memes, comments and collections are
     * gone for good. That is not an INFO line.
     */
    boolean lastSagaWasCompensated(String email);
}
