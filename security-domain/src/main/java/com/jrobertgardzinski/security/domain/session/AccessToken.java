package com.jrobertgardzinski.security.domain.session;

import com.jrobertgardzinski.security.domain.core.AbstractToken;

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
