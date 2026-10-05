package com.jrobertgardzinski.security.domain.vo.token;

/**
 * Single-use token e-mailed to a user so they can set a new password after forgetting the old one.
 */
public final class PasswordResetToken extends AbstractToken {

    public PasswordResetToken(String value) {
        super(value);
    }

    public static PasswordResetToken random() {
        return new PasswordResetToken(randomValue());
    }
}
