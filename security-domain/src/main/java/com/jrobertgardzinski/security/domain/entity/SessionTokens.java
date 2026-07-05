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

    public static SessionTokens createFor(Email email, SessionTokensConfig config, Clock clock) {
        return createFor(email, config, clock, AccessTokenMint.RANDOM);
    }

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
