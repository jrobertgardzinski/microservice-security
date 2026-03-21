package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.security.domain.vo.Email;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.Token;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.TokenExpiration;


import java.time.Clock;
import java.time.LocalDateTime;

public record SessionTokens(
        Email email,
        RefreshToken refreshToken,
        AccessToken accessToken,
        RefreshTokenExpiration refreshTokenExpiration,
        AuthorizationTokenExpiration authorizationTokenExpiration) {

    public static SessionTokens createFor(Email email, int refreshTokenHours, int accessTokenHours, Clock clock) {
        return new SessionTokens(
                email,
                new RefreshToken(Token.random()),
                new AccessToken(Token.random()),
                new RefreshTokenExpiration(TokenExpiration.validInHours(refreshTokenHours, clock)),
                new AuthorizationTokenExpiration(TokenExpiration.validInHours(accessTokenHours, clock))
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
