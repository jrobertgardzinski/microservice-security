package com.jrobertgardzinski.security.domain.vo.token;

import java.util.Objects;

public abstract class AbstractTokenValidityInHours {

    public static final int MIN = 1;

    private final int value;

    /**
     * @param max the ceiling THIS kind of token accepts. There was none, so a deployment could ask
     *            for 87600 hours and get it: a session valid for a decade, from one typo in a
     *            property, with nothing to notice it. The floor is shared because a token valid for
     *            zero hours is nonsense whatever it is for; the ceiling is not, because an access
     *            token and a refresh token have different jobs and the number that is absurd for
     *            one is ordinary for the other.
     */
    protected AbstractTokenValidityInHours(int value, int max) {
        if (value < MIN) throw new IllegalArgumentException("TokenValidityInHours must be >= " + MIN);
        if (value > max) throw new IllegalArgumentException("TokenValidityInHours must be <= " + max);
        this.value = value;
    }

    public Integer value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTokenValidityInHours other)) return false;
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
