package com.jrobertgardzinski.security.domain.session;

public record SessionTokensConfig(
        RefreshTokenValidityInHours refreshTokenValidityInHours,
        AccessTokenValidityInHours accessTokenValidityInHours
) {
}
