package com.jrobertgardzinski.security.config.bruteforce;

import com.jrobertgardzinski.security.config.bruteforce.vo.FailureWindowMinutes;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxBlockMinutes;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxFailures;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxFailuresPerSource;
import com.jrobertgardzinski.security.config.bruteforce.vo.MinBlockMinutes;

/**
 * Two limits inside one window, because one limit is always walkable-around: {@link MaxFailures}
 * counts this source against ONE account (someone guessing a password), and
 * {@link MaxFailuresPerSource} counts it against ANY account (someone spraying a few guesses across
 * many). A ceiling below the per-account limit would make the tighter number unreachable and is
 * refused here rather than discovered later.
 */
public record BruteForceConfig(FailureWindowMinutes failureWindowMinutes,
                               MaxFailures maxFailures,
                               MaxFailuresPerSource maxFailuresPerSource,
                               MinBlockMinutes minBlockMinutes,
                               MaxBlockMinutes maxBlockMinutes) {

    public BruteForceConfig {
        if (maxBlockMinutes.value() < minBlockMinutes.value())
            throw new IllegalArgumentException("maxBlockMinutes must be >= minBlockMinutes");
        if (maxFailuresPerSource.value() < maxFailures.value())
            throw new IllegalArgumentException(
                    "maxFailuresPerSource (" + maxFailuresPerSource.value() + ") must be >= maxFailures ("
                            + maxFailures.value() + "): a ceiling below the per-account limit would block"
                            + " the whole address before a single account ever reached its own limit");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private FailureWindowMinutes failureWindowMinutes = FailureWindowMinutes.DEFAULT;
        private MaxFailures maxFailures = MaxFailures.DEFAULT;
        private MaxFailuresPerSource maxFailuresPerSource = MaxFailuresPerSource.DEFAULT;
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

        public Builder maxFailuresPerSource(int maxFailuresPerSource) {
            this.maxFailuresPerSource = new MaxFailuresPerSource(maxFailuresPerSource);
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
            return new BruteForceConfig(failureWindowMinutes, maxFailures, maxFailuresPerSource,
                    minBlockMinutes, maxBlockMinutes);
        }
    }
}
