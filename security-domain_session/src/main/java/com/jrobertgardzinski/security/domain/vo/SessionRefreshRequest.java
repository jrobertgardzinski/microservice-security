package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

/**
 * A request to renew a session without re-authenticating. The refresh token is the only thing the
 * client presents — the session (and thus the user) is found from it.
 */
public record SessionRefreshRequest(RefreshToken refreshToken) {
}
