package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Tracks pending password resets. A pending reset remembers the (hashed) token last e-mailed to an
 * address; consuming it with the matching token yields that address once, and single-use — the
 * token no longer works afterwards. Raw tokens are never stored.
 */
public interface PasswordResetRepository {

    /**
     * A consumed reset: the address it was requested for and WHEN it was requested. The age is part
     * of the answer because single use is not enough on its own — a link that never expires outlives
     * the account it was issued for, and would set the password of whoever registers that address
     * next.
     */
    record PendingReset(Email email, LocalDateTime requestedAt) {}

    /** Remember (or reset) the pending token e-mailed to this address. */
    void startReset(Email email, PasswordResetToken token);

    /** If the token matches a pending reset, consume it (single-use) and return it; else empty. */
    Optional<PendingReset> consumeReset(PasswordResetToken token);

    /**
     * Drops any pending reset for this address. Called when the account is deleted and when it moves
     * to another address: the link was e-mailed to an address the account no longer owns, and a live
     * token must not survive the account it was issued for.
     */
    void purge(Email email);
}
