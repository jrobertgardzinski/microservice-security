package com.jrobertgardzinski.security.domain.vo;

import java.util.Objects;

/**
 * One-time password code after hashing. Safe to persist on the auth session.
 */
public record HashedOtpCode(String value) {

    public HashedOtpCode {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Hashed OTP must not be blank");
        }
    }
}
