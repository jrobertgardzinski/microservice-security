package com.jrobertgardzinski.security.config.mfa;

/**
 * The lifecycle of an MFA challenge code (e-mail / SMS): how long a code lives, how many wrong
 * proofs a single sign-in ticket tolerates before it is torn down, and how many digits the code
 * has. Config, not constants — overridable per deployment ({@code security.mfa.code.*}), same as
 * the brute-force policy and the throttle windows. The defaults are a sane starting point, not a
 * hard-coded rule.
 */
public record ChallengeCodeConfig(int codeTtlMinutes, int maxAttempts, int codeLength) {

    public ChallengeCodeConfig {
        if (codeTtlMinutes <= 0) {
            throw new IllegalArgumentException("codeTtlMinutes must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (codeLength < 4 || codeLength > 10) {
            throw new IllegalArgumentException("codeLength must be between 4 and 10");
        }
    }

    public static ChallengeCodeConfig withDefaults() {
        return new ChallengeCodeConfig(5, 5, 6);
    }
}
