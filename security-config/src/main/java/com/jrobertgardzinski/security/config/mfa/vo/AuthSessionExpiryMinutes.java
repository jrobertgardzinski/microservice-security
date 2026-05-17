package com.jrobertgardzinski.security.config.mfa.vo;

public record AuthSessionExpiryMinutes(int value) {
    public static final int MIN = 1;
    public static final int MAX = 60;
    public static final AuthSessionExpiryMinutes DEFAULT = new AuthSessionExpiryMinutes(10);

    public AuthSessionExpiryMinutes {
        if (value < MIN || value > MAX) throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
