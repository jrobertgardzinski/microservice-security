package com.jrobertgardzinski.security.domain.vo.token.expiration;

import com.jrobertgardzinski.security.domain.vo.token.AbstractTokenValidityInHours;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Point-in-time expiration for a token.
 * Use {@link #validInHours(AbstractTokenValidityInHours, Clock)} to create from a duration.
 */
public record TokenExpiration(LocalDateTime value) {

    public TokenExpiration {
        if (value == null) {
            throw new IllegalArgumentException("TokenExpiration value must not be null");
        }
    }

    public static TokenExpiration validInHours(AbstractTokenValidityInHours hours, Clock clock) {
        return new TokenExpiration(LocalDateTime.now(clock).plusHours(hours.value()));
    }
}
