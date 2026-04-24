package com.jrobertgardzinski.security.domain.vo.token;

/**
 * Long-lived refresh token used to obtain a new access token without re-authentication.
 */
public final class RefreshToken extends AbstractToken {

    public RefreshToken(String value) {
        super(value);
    }

    public static RefreshToken random() {
        return new RefreshToken(randomValue());
    }
}
