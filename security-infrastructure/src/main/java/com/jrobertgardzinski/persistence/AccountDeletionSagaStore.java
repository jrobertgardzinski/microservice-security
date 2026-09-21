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
 *
 * <p>An outcome settles the saga it NAMES, not the address it carries: an address is a person, and
 * a person can ask twice. A late outcome of a deletion this service had already given up on used to
 * settle whatever deletion was running for that person next — a FIRST announcement, so no amount of
 * deduplicating on the outcome id stopped it. The portal echoes security's saga id back for exactly
 * this, but only when the fact it answers carried one, so a null {@code sagaId} still means the old
 * address-only match: an outcome nobody can correlate is worth acting on, and dropping it would
 * leave the account locked until the safety net fires.
 */
public interface AccountDeletionSagaStore {

    /**
     * Opens a saga for this address — at most ONE runs at a time. Returns true for the call that
     * opened it, false when a saga for the address is already STARTED (a duplicate request: a
     * second live session, a retry).
     *
     * <p>The one-at-a-time part is not decoration. An outcome that names no saga is settled by
     * {@code email AND state = 'STARTED'} alone, so two STARTED rows for one address mean such an
     * outcome settles whichever it finds: an account deleted for good on a confirmation belonging
     * to another case. Postgres holds the invariant with a partial unique index (V22); this
     * contract is what the in-memory store has to match, or the tests ride semantics production
     * does not have.
     */
    boolean start(UUID sagaId, String email, Instant at);

    /**
     * STARTED → COMPLETED for the saga the outcome NAMES; true only for the call that did it.
     *
     * <p>A null {@code sagaId} settles this email's running saga instead, which is what both of
     * these did for every caller — see the note above.
     */
    boolean complete(UUID sagaId, String email, Instant at);

    /** STARTED → COMPENSATED for the saga the outcome names; true only for the call that did it. */
    boolean compensate(UUID sagaId, String email, Instant at);

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
