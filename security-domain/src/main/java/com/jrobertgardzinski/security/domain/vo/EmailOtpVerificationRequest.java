package com.jrobertgardzinski.security.domain.vo;

import java.util.Objects;

/**
 * A user's second-factor proof: the auth session they're continuing and the
 * OTP code they pulled from their inbox.
 */
public record EmailOtpVerificationRequest(AuthSessionId authSessionId, OtpCode code) {

    public EmailOtpVerificationRequest {
        Objects.requireNonNull(authSessionId);
        Objects.requireNonNull(code);
    }
}
