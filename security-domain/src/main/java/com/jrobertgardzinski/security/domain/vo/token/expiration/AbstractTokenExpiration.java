package com.jrobertgardzinski.security.domain.vo.token.expiration;

import com.jrobertgardzinski.security.domain.vo.token.AbstractTokenValidityInHours;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public abstract class AbstractTokenExpiration {

    private final LocalDateTime value;

    protected AbstractTokenExpiration(LocalDateTime value) {
        if (value == null) throw new IllegalArgumentException("TokenExpiration value must not be null");
        this.value = value;
    }

    public LocalDateTime value() {
        return value;
    }

    public boolean hasExpired(Clock clock) {
        return LocalDateTime.now(clock).isAfter(value);
    }

    protected static LocalDateTime plusHours(AbstractTokenValidityInHours hours, Clock clock) {
        return LocalDateTime.now(clock).plusHours(hours.value());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTokenExpiration other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
