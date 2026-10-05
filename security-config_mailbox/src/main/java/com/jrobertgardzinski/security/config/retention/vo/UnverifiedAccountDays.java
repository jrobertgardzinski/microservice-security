package com.jrobertgardzinski.security.config.retention.vo;

import com.jrobertgardzinski.config.ConfigValue;

/**
 * How long an account whose address was NEVER verified is kept before it is deleted.
 *
 * <p>The number is a deployment's to choose, and the one shipped here is a default, not a rule: the
 * law states a principle — personal data is kept no longer than the purpose needs (GDPR art. 5(1)(e))
 * — and leaves the period to whoever can say what the purpose is. Thirty days is what this service
 * ships with because it is the same window its verification ROWS already use, so one number tells
 * one story: a month after somebody typed an address and never confirmed it, nothing of that attempt
 * remains — neither the pending token nor the account it was for.
 *
 * <p>It sits at the REBUILD and RESTART levels and deliberately not at the live one. Shortening a
 * retention period deletes accounts, and a deletion that can be triggered from a web form without a
 * release or a deployment is a button nobody meant to build.
 */
public record UnverifiedAccountDays(Integer value) implements ConfigValue<Integer> {

    /** The name this period goes by on every level of a deployment's configuration ladder. */
    public static final String KEY = "security.retention.unverified.account.days";

    /**
     * Below this, a person who registers on Friday and reads their mail after a holiday finds no
     * account — and the verification link itself lives 48 hours, so anything under a few days makes
     * the account outlive nothing but its own link.
     */
    public static final int MIN = 3;

    /** Past a year, "kept no longer than necessary" stops being arguable for an address nobody confirmed. */
    public static final int MAX = 365;

    public static final UnverifiedAccountDays DEFAULT = new UnverifiedAccountDays(30);

    public UnverifiedAccountDays {
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
    public UnverifiedAccountDays holding(Integer value) {
        return new UnverifiedAccountDays(value);
    }
}
