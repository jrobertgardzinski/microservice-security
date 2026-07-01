package com.jrobertgardzinski.security.domain.vo.token;

import java.util.Objects;
import java.util.UUID;

public abstract class AbstractToken {

    private final String value;

    protected AbstractToken(String value) {
        if (value.isBlank()) throw new IllegalArgumentException("Token value must not be blank");
        this.value = value;
    }

    public String value() {
        return value;
    }

    protected static String randomValue() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractToken other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
