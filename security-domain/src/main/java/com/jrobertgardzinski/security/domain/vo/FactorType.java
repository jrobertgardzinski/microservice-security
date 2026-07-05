package com.jrobertgardzinski.security.domain.vo;

/**
 * Which kind of authentication factor — a stable string id, not a domain enum, on purpose: adding
 * a new method (TOTP, WebAuthn, a hardware token…) is a new adapter keyed by its type, and must
 * never force an edit to a central enum. That is the plug-and-play seam. The constants below are
 * the ones the system knows how to wire today; an unknown-but-valid id is a factor no adapter
 * serves yet, not an error.
 */
public record FactorType(String value) {

    public static final FactorType EMAIL_CODE = new FactorType("EMAIL_CODE");
    public static final FactorType SMS_CODE = new FactorType("SMS_CODE");
    public static final FactorType TOTP = new FactorType("TOTP");

    public FactorType {
        if (value.isBlank()) {
            throw new IllegalArgumentException("factor type must not be blank");
        }
    }

    public static FactorType of(String value) {
        return new FactorType(value);
    }
}
