package com.jrobertgardzinski.security.config.session.vo;

import com.jrobertgardzinski.config.ConfigValue;

/**
 * How long a sign-in may live in total, however often it is refreshed.
 *
 * <p>Rotation gives each refresh a fresh window, and nothing capped the sum: a session touched once
 * a day survived for ever, so "signed in since last March" was a state this service could reach and
 * never reconsider. The refresh token's own validity answers a different question — how long a
 * session may sit IDLE — and no number of idle-windows adds up to a promise that the person is
 * still there, still employed, still the same person at that address.
 *
 * <p>Counted from when the LINEAGE started, not from the last refresh: that is what makes it
 * absolute. When it runs out the family is revoked and the answer is the ordinary "your session
 * expired" — signing in again is all it takes.
 */
public record MaxSessionLifetimeHours(Integer value) implements ConfigValue<Integer> {

    /** The name this ceiling goes by on every level of a deployment's configuration ladder. */
    public static final String KEY = "security.session.max.lifetime.hours";

    /** Below an hour it would expire sessions faster than the access tokens they mint. */
    public static final int MIN = 1;

    /** A year. Past that the ceiling stops being a ceiling and starts being decoration. */
    public static final int MAX = 8760;

    /**
     * Thirty days. Long enough that a phone kept in a pocket is not signed out every week, short
     * enough that a session nobody has re-authenticated inside a month is asked to prove itself
     * once — which is the entire point.
     */
    public static final MaxSessionLifetimeHours DEFAULT = new MaxSessionLifetimeHours(720);

    public MaxSessionLifetimeHours {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Integer defaultValue() {
        return DEFAULT.value();
    }

    @Override
    public MaxSessionLifetimeHours holding(Integer value) {
        return new MaxSessionLifetimeHours(value);
    }
}
