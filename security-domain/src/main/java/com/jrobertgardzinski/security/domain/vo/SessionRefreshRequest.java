package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

public record SessionRefreshRequest(Email email, RefreshToken refreshToken) {
}
