package com.jrobertgardzinski.security.domain.vo.token;

public record TokenValidityInHours(int value) {
    public static final int MIN = 1;

    public TokenValidityInHours {
        if (value < MIN) throw new IllegalArgumentException("TokenValidityHours must be >= " + MIN);
    }
}
