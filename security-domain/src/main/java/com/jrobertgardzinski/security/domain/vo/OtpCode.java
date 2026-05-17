package com.jrobertgardzinski.security.domain.vo;

import java.util.Objects;

/**
 * One-time password value as presented by the user. Plaintext, short-lived,
 * never persisted — only its {@link HashedOtpCode} is stored on the auth session.
 */
public record OtpCode(String value) {

    public OtpCode {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("OTP code must not be blank");
        }
    }

    public static final String REDACTED = "REDACTED";

    /** Redacted intentionally — never log OTP codes. */
    @Override
    public String toString() {
        return "OtpCode[value=" + REDACTED + "]";
    }
}
