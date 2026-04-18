package com.jrobertgardzinski.security.config;

public record SessionTokensConfig(RefreshTokenValidityHours refreshTokenValidityHours, AccessTokenValidityHours accessTokenValidityHours) {
}
