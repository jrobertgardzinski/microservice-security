package com.jrobertgardzinski.security.config;

public record AccessTokenValidityHours(int value) {

    public AccessTokenValidityHours {
        if (value < 1) throw new IllegalArgumentException("accessTokenValidityHours must be at least 1");
    }
}
