package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * An active session represented by a pair of tokens.
 */
public record SessionTokens(
        Email email,
        RefreshToken refreshToken,
        AccessToken accessToken,
        RefreshTokenExpiration refreshTokenExpiration,
        AuthorizationTokenExpiration authorizationTokenExpiration) {

    /**
     * @param mint how the access token is made — and it is a PARAMETER because there is no sensible
     *             default. There used to be an overload without it that quietly chose
     *             {@code AccessTokenMint.RANDOM}; nothing in production ever called it (every path
     *             mints a JWT), so its only effect was that a handful of tests exercised a token
     *             this service does not issue, while reading as though they exercised the real one.
     */
    public static SessionTokens createFor(Email email, SessionTokensConfig config, Clock clock, AccessTokenMint mint) {
        AuthorizationTokenExpiration accessExpiration =
                AuthorizationTokenExpiration.validInHours(config.accessTokenValidityInHours(), clock);
        return new SessionTokens(
                email,
                RefreshToken.random(),
                mint.mint(email, accessExpiration),
                RefreshTokenExpiration.validInHours(config.refreshTokenValidityInHours(), clock),
                accessExpiration
        );
    }

    public String plainEmail() {
        return email.value();
    }

    public String plainRefreshToken() {
        return refreshToken.value();
    }

    public String plainAccessToken() {
        return accessToken.value();
    }

    public LocalDateTime plainRefreshTokenExpiration() {
        return refreshTokenExpiration.value();
    }

    public LocalDateTime plainAuthorizationTokenExpiration() {
        return authorizationTokenExpiration.value();
    }
}
