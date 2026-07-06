package com.jrobertgardzinski.security.config.mfa;

/**
 * The shape of a recovery-code batch: how many codes a user gets and how many characters each
 * code carries (excluding the group separators). Config, not constants — overridable per
 * deployment ({@code security.mfa.recovery.*}), same as the challenge-code lifecycle. Recovery
 * codes are the break-glass ALTERNATIVE to a chain link, not a link themselves; the count is a
 * balance between lockout insurance and the number of live secrets on a printout.
 */
public record RecoveryCodeConfig(int count, int length) {

    public RecoveryCodeConfig {
        if (count <= 0 || count > 50) {
            throw new IllegalArgumentException("count must be between 1 and 50");
        }
        if (length < 8 || length > 40) {
            throw new IllegalArgumentException("length must be between 8 and 40");
        }
    }

    public static RecoveryCodeConfig withDefaults() {
        return new RecoveryCodeConfig(10, 10);
    }
}
