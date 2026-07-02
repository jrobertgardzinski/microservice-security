package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.util.Optional;

/**
 * Tracks pending e-mail verifications and their outcome. A pending verification remembers the
 * (hashed) token last e-mailed to an address; completing it with the matching token marks the
 * address verified and consumes the token. Raw tokens are never stored.
 */
public interface EmailVerificationRepository {

    /** Remember (or reset) the pending token e-mailed to this address; the address is not yet verified. */
    void startVerification(Email email, VerificationToken token);

    /** If the token matches a pending verification, mark that address verified and return it; else empty. */
    Optional<Email> completeVerification(VerificationToken token);

    /**
     * Mark the address verified without a token — for flows that proved ownership by other means
     * (e.g. confirming an email change, whose own token was delivered to that address).
     */
    void markVerified(Email email);

    boolean isVerified(Email email);
}
