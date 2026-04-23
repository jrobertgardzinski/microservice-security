package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.AbstractTokenValidityInHours;

public final class RefreshTokenValidityInHours extends AbstractTokenValidityInHours {

    public RefreshTokenValidityInHours(int value) {
        super(value);
    }
}
