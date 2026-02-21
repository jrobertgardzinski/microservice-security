package com.jrobertgardzinski.email.domain;

import java.util.Objects;

/**
 * The domain part of an email address — everything after '@', always lowercase.
 *
 * Enforces only structural invariants: non-empty, contains at least one '.'.
 * Public suffix awareness (co.uk, com.pl) belongs in email-specifications.
 */
public final class DomainPart {

    private final String value;

    private DomainPart(String value) {
        this.value = value;
    }

    public static DomainPart of(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Email domain must not be empty");
        }
        String lower = value.toLowerCase();
        if (!lower.contains(".")) {
            throw new IllegalArgumentException("Email domain must contain at least one '.': " + value);
        }
        return new DomainPart(lower);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomainPart other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
