package com.jrobertgardzinski.password.domain;

import java.util.List;
import java.util.Objects;

/**
 * Plaintext password value object.
 *
 * Enforces only that a policy is satisfied — the policy decides the rules.
 * Never exposes the raw value in toString() to prevent accidental logging.
 */
public final class PlaintextPassword {

    private final String value;

    private PlaintextPassword(String value) {
        this.value = value;
    }

    public static PlaintextPassword of(String value, PasswordPolicy policy) {
        PlaintextPassword candidate = new PlaintextPassword(value);
        List<String> errors = policy.validate(candidate);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.toString());
        }
        return candidate;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaintextPassword other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /** Redacted intentionally — never log plaintext passwords. */
    @Override
    public String toString() {
        return "PlaintextPassword[value=REDACTED]";
    }
}
