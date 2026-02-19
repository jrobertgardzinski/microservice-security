package com.jrobertgardzinski.security.domain.vo;

import java.time.Clock;
import java.time.LocalDateTime;

public record TokenExpiration(LocalDateTime value) {
    public TokenExpiration {
        if (value == null) {
            throw new IllegalArgumentException("'expiration' cannot be null");
        }
    }

    public static TokenExpiration validInHours(int hours, Clock clock) {
        return new TokenExpiration(LocalDateTime.now(clock).plusHours(hours));
    }
}
