package com.jrobertgardzinski.security.domain.vo.token.expiration;

import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The point in time at which an {@link AccessToken} expires.
 */
public final class AuthorizationTokenExpiration extends AbstractTokenExpiration {

    public AuthorizationTokenExpiration(LocalDateTime value) {
        super(value);
    }

    public static AuthorizationTokenExpiration validInHours(AccessTokenValidityInHours hours, Clock clock) {
        return new AuthorizationTokenExpiration(plusHours(hours, clock));
    }
}
