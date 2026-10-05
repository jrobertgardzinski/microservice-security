package com.jrobertgardzinski.security.domain.vo.token;

/**
 * Single-use token e-mailed to a user to prove they own their e-mail address.
 */
public final class VerificationToken extends AbstractToken {

    public VerificationToken(String value) {
        super(value);
    }

    public static VerificationToken random() {
        return new VerificationToken(randomValue());
    }
}
