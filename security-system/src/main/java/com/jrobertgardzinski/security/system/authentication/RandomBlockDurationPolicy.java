package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Production {@link BlockDurationPolicy}: a random duration within the configured
 * {@code [minBlockMinutes, maxBlockMinutes]} range, so block lengths are not predictable.
 */
public final class RandomBlockDurationPolicy implements BlockDurationPolicy {

    private final BruteForceConfig config;

    public RandomBlockDurationPolicy(BruteForceConfig config) {
        this.config = config;
    }

    @Override
    public int blockMinutes() {
        return ThreadLocalRandom.current().nextInt(
                config.minBlockMinutes().value(),
                config.maxBlockMinutes().value() + 1);
    }
}
