package com.jrobertgardzinski.security.config.bruteforce.vo;

public record MaxBlockMinutes(int value) {
    public static final int MIN = 1;
    public static final int MAX = 1440;
    public static final MaxBlockMinutes DEFAULT = new MaxBlockMinutes(10);

    public MaxBlockMinutes {
        if (value < MIN || value > MAX) throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
