package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.util.Optional;

/**
 * Tracks pending email changes. A pending change remembers the (hashed) token e-mailed to the new
 * address; confirming it with the matching token yields the change once, single-use. Raw tokens are
 * never stored.
 */
public interface EmailChangeRepository {

    void startChange(EmailChange change, VerificationToken token);

    /**
     * The ticket, and the moment it was issued — because when it was issued is half the answer.
     *
     * <p>Single use alone is not enough: a link that never expires outlives the reason it was sent.
     * This one MOVES the account, so a stale mail sitting in an old inbox is a way in that survives
     * every later precaution — and it read as valid for months, because nothing was looking at the
     * date. The window itself is a decision, so it is made where decisions are made: in the use
     * case, not here and not in the adapter.
     */
    Optional<PendingEmailChange> confirmChange(VerificationToken token);

    /** A pending move together with the moment it started. */
    record PendingEmailChange(EmailChange change, java.time.LocalDateTime startedAt) {}

    /**
     * Drops every pending change that mentions this address, as EITHER end of a move. Called when the
     * account is deleted and when it moves: a change still pending for an address the account no
     * longer owns is a live token pointing at a stranger's account, and the addresses are personal
     * data that must not outlive the account.
     */
    void purge(Email email);
}
