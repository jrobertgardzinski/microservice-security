package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

/**
 * A request to renew an active session without re-authenticating.
 */
public record SessionRefreshRequest(Email email, RefreshToken refreshToken) {
}
