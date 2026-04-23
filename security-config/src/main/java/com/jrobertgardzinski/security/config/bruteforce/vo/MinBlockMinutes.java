package com.jrobertgardzinski.security.config.bruteforce.vo;

public record MinBlockMinutes(int value) {
    public static final int MIN = 1;
    public static final int MAX = 60;
    public static final MinBlockMinutes DEFAULT = new MinBlockMinutes(3);

    public MinBlockMinutes {
        if (value < MIN || value > MAX) throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
