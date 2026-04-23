package com.jrobertgardzinski.security.config.bruteforce.vo;

public record FailureWindowMinutes(int value) {
    public static final int MIN = 3;
    public static final int MAX = 120;
    public static final FailureWindowMinutes DEFAULT = new FailureWindowMinutes(15);

    public FailureWindowMinutes {
        if (value < MIN || value > MAX) throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
