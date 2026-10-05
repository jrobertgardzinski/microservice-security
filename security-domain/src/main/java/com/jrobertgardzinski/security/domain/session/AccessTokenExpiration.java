package com.jrobertgardzinski.security.domain.session;

import com.jrobertgardzinski.security.domain.core.AbstractTokenExpiration;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The point in time at which an {@link AccessToken} expires.
 */
public final class AccessTokenExpiration extends AbstractTokenExpiration {

    public AccessTokenExpiration(LocalDateTime value) {
        super(value);
    }

    public static AccessTokenExpiration validInHours(AccessTokenValidityInHours hours, Clock clock) {
        return new AccessTokenExpiration(plusHours(hours, clock));
    }
}
