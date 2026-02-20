package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.token.domain.RefreshToken;

public record SessionRefreshRequest(Email email, RefreshToken refreshToken) {
}
