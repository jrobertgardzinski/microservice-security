package com.jrobertgardzinski.security.config.mfa.vo;

public record OtpExpiryMinutes(int value) {
    public static final int MIN = 1;
    public static final int MAX = 30;
    public static final OtpExpiryMinutes DEFAULT = new OtpExpiryMinutes(5);

    public OtpExpiryMinutes {
        if (value < MIN || value > MAX) throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
