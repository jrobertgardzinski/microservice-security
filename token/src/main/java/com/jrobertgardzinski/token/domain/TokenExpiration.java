package com.jrobertgardzinski.token.domain;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Point-in-time expiration for a token.
 * Use {@link #validInHours(int, Clock)} to create from a duration.
 */
public record TokenExpiration(LocalDateTime value) {

    public TokenExpiration {
        if (value == null) {
            throw new IllegalArgumentException("TokenExpiration value must not be null");
        }
    }

    public static TokenExpiration validInHours(int hours, Clock clock) {
        return new TokenExpiration(LocalDateTime.now(clock).plusHours(hours));
    }
}
