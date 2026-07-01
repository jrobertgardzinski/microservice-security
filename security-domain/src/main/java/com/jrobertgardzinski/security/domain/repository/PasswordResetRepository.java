package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

import java.util.Optional;

/**
 * Tracks pending password resets. A pending reset remembers the (hashed) token last e-mailed to an
 * address; consuming it with the matching token yields that address once, and single-use — the
 * token no longer works afterwards. Raw tokens are never stored.
 */
public interface PasswordResetRepository {

    /** Remember (or reset) the pending token e-mailed to this address. */
    void startReset(Email email, PasswordResetToken token);

    /** If the token matches a pending reset, consume it (single-use) and return the address; else empty. */
    Optional<Email> consumeReset(PasswordResetToken token);
}
