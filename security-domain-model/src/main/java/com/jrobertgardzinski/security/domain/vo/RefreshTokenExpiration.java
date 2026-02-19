package com.jrobertgardzinski.security.domain.vo;

import java.time.Clock;
import java.time.LocalDateTime;

public record RefreshTokenExpiration(TokenExpiration value) {
    public boolean hasExpired(Clock clock) {
        return LocalDateTime.now(clock).isAfter(value.value());
    }
}
