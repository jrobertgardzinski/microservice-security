package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.AbstractTokenValidityInHours;

public final class AccessTokenValidityInHours extends AbstractTokenValidityInHours {

    public AccessTokenValidityInHours(int value) {
        super(value);
    }
}
