package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.util.constraint.ErrorConstraint;

/**
 * Stand-in for the password policy in system-level tests.
 *
 * Real policy lives in password-security-system. Once a password-security-config
 * is plugged into security-system, replace this with the configured constraints.
 */
final class MinLengthAtLeast extends ErrorConstraint<PlaintextPassword> {

    private final int min;

    MinLengthAtLeast(int min) {
        this.min = min;
    }

    @Override
    public boolean isSatisfied(PlaintextPassword candidate) {
        return candidate.value().length() >= min;
    }

    @Override
    public String code() {
        return "MIN_LENGTH_AT_LEAST_" + min;
    }
}
