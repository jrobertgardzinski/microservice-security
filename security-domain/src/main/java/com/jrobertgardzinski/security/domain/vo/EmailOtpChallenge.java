package com.jrobertgardzinski.security.domain.vo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pending challenge state for the EMAIL_OTP factor — the hashed code we sent
 * to the user and the moment it stops being acceptable.
 */
public record EmailOtpChallenge(HashedOtpCode codeHash, LocalDateTime expiresAt) {

    public EmailOtpChallenge {
        Objects.requireNonNull(codeHash);
        Objects.requireNonNull(expiresAt);
    }

    public boolean isExpired(java.time.Clock clock) {
        return !expiresAt.isAfter(LocalDateTime.now(clock));
    }
}
