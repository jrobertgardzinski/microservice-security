package com.jrobertgardzinski.security.domain.config;

public record BruteForceConfig(int failureWindowMinutes, int maxFailures, int minBlockMinutes, int maxBlockMinutes) {
    public BruteForceConfig {
        if (failureWindowMinutes < 1) throw new IllegalArgumentException("failureWindowMinutes must be at least 1");
        if (maxFailures < 1) throw new IllegalArgumentException("maxFailures must be at least 1");
        if (minBlockMinutes < 1) throw new IllegalArgumentException("minBlockMinutes must be at least 1");
        if (maxBlockMinutes < minBlockMinutes) throw new IllegalArgumentException("maxBlockMinutes must be >= minBlockMinutes");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int failureWindowMinutes = 15;
        private int maxFailures = 3;
        private int minBlockMinutes = 3;
        private int maxBlockMinutes = 10;

        public Builder failureWindowMinutes(int failureWindowMinutes) {
            this.failureWindowMinutes = failureWindowMinutes;
            return this;
        }

        public Builder maxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
            return this;
        }

        public Builder minBlockMinutes(int minBlockMinutes) {
            this.minBlockMinutes = minBlockMinutes;
            return this;
        }

        public Builder maxBlockMinutes(int maxBlockMinutes) {
            this.maxBlockMinutes = maxBlockMinutes;
            return this;
        }

        public BruteForceConfig build() {
            return new BruteForceConfig(failureWindowMinutes, maxFailures, minBlockMinutes, maxBlockMinutes);
        }
    }
}
