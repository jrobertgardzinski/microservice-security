package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.TokenValidityInHours;

public record RefreshTokenValidityInHours(TokenValidityInHours tokenValidityInHours) {

    public RefreshTokenValidityInHours {
        if (tokenValidityInHours.value() < 1) throw new IllegalArgumentException("refreshTokenValidityHours must be at least 1");
    }
}
