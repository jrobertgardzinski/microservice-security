package com.jrobertgardzinski.security.domain.vo;

public record SessionTokensConfig(
        RefreshTokenValidityInHours refreshTokenValidityInHours,
        AccessTokenValidityInHours accessTokenValidityInHours
) {
}
