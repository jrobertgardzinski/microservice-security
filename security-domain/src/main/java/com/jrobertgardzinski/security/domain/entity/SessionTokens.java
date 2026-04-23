package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.Token;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.TokenExpiration;


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
        RefreshTokenExpiration refreshTokenExpiration = new RefreshTokenExpiration(
                TokenExpiration.validInHours(config.refreshTokenValidityInHours(), clock));
        AuthorizationTokenExpiration authorizationTokenExpiration = new AuthorizationTokenExpiration(
                TokenExpiration.validInHours(config.accessTokenValidityInHours(), clock));

        return new SessionTokens(
                email,
                new RefreshToken(Token.random()),
                new AccessToken(Token.random()),
                refreshTokenExpiration,
                authorizationTokenExpiration
        );
    }

    public String plainEmail() {
        return email.value();
    }

    public String plainRefreshToken() {
        return refreshToken.value().value();
    }

    public String plainAccessToken() {
        return accessToken.value().value();
    }

    public LocalDateTime plainRefreshTokenExpiration() {
        return refreshTokenExpiration.value().value();
    }

    public LocalDateTime plainAuthorizationTokenExpiration() {
        return authorizationTokenExpiration.value().value();
    }
}
