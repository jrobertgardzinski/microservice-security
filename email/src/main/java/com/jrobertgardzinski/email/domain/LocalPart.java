package com.jrobertgardzinski.email.domain;

import java.util.Objects;

/** The local part of an email address — everything before '@'. */
public final class LocalPart {

    private final String value;

    private LocalPart(String value) {
        this.value = value;
    }

    public static LocalPart of(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Email local part must not be empty");
        }
        return new LocalPart(value);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalPart other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
