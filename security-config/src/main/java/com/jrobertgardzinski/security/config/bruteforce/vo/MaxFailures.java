package com.jrobertgardzinski.security.config.bruteforce.vo;

public record MaxFailures(int value) {
    public static final int MIN = 1;
    public static final int MAX = 20;
    public static final MaxFailures DEFAULT = new MaxFailures(3);

    public MaxFailures {
        if (value < MIN || value > MAX) throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
