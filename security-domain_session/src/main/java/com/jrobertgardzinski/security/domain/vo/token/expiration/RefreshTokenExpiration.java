package com.jrobertgardzinski.security.domain.vo.token.expiration;

import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Expiration of a refresh token.
 */
public final class RefreshTokenExpiration extends AbstractTokenExpiration {

    public RefreshTokenExpiration(LocalDateTime value) {
        super(value);
    }

    public static RefreshTokenExpiration validInHours(RefreshTokenValidityInHours hours, Clock clock) {
        return new RefreshTokenExpiration(plusHours(hours, clock));
    }
}
