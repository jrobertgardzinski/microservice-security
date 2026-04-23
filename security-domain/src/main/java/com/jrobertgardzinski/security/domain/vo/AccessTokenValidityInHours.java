package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.TokenValidityInHours;

public record AccessTokenValidityInHours(TokenValidityInHours tokenValidityInHours) {

    public AccessTokenValidityInHours {
        if (tokenValidityInHours.value() < 1) throw new IllegalArgumentException("accessTokenValidityHours must be at least 1");
    }
}
