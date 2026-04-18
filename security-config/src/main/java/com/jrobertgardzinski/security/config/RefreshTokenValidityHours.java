package com.jrobertgardzinski.security.config;

public record RefreshTokenValidityHours(int value) {

    public RefreshTokenValidityHours {
        if (value < 1) throw new IllegalArgumentException("refreshTokenValidityHours must be at least 1");
    }
}
