package com.jrobertgardzinski.token.domain;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Expiration of a refresh token. Can check whether it has already expired.
 */
public record RefreshTokenExpiration(TokenExpiration value) {

    public boolean hasExpired(Clock clock) {
        return LocalDateTime.now(clock).isAfter(value.value());
    }
}
