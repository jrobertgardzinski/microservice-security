package com.jrobertgardzinski.security.domain.vo.token;

/**
 * Short-lived access token issued after successful authentication.
 */
public final class AccessToken extends AbstractToken {

    public AccessToken(String value) {
        super(value);
    }

    public static AccessToken random() {
        return new AccessToken(randomValue());
    }
}
