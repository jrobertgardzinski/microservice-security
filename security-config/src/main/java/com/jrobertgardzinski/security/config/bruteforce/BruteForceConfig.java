package com.jrobertgardzinski.security.config.bruteforce;

import com.jrobertgardzinski.security.config.bruteforce.vo.FailureWindowMinutes;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxBlockMinutes;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxFailures;
import com.jrobertgardzinski.security.config.bruteforce.vo.MinBlockMinutes;

public record BruteForceConfig(FailureWindowMinutes failureWindowMinutes,
                               MaxFailures maxFailures,
                               MinBlockMinutes minBlockMinutes,
                               MaxBlockMinutes maxBlockMinutes) {

    public BruteForceConfig {
        if (maxBlockMinutes.value() < minBlockMinutes.value())
            throw new IllegalArgumentException("maxBlockMinutes must be >= minBlockMinutes");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private FailureWindowMinutes failureWindowMinutes = FailureWindowMinutes.DEFAULT;
        private MaxFailures maxFailures = MaxFailures.DEFAULT;
        private MinBlockMinutes minBlockMinutes = MinBlockMinutes.DEFAULT;
        private MaxBlockMinutes maxBlockMinutes = MaxBlockMinutes.DEFAULT;

        public Builder failureWindowMinutes(int failureWindowMinutes) {
            this.failureWindowMinutes = new FailureWindowMinutes(failureWindowMinutes);
            return this;
        }

        public Builder maxFailures(int maxFailures) {
            this.maxFailures = new MaxFailures(maxFailures);
            return this;
        }

        public Builder minBlockMinutes(int minBlockMinutes) {
            this.minBlockMinutes = new MinBlockMinutes(minBlockMinutes);
            return this;
        }

        public Builder maxBlockMinutes(int maxBlockMinutes) {
            this.maxBlockMinutes = new MaxBlockMinutes(maxBlockMinutes);
            return this;
        }

        public BruteForceConfig build() {
            return new BruteForceConfig(failureWindowMinutes, maxFailures, minBlockMinutes, maxBlockMinutes);
        }
    }
}
