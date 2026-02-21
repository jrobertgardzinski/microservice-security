package com.jrobertgardzinski.token.domain;

/**
 * Long-lived refresh token used to obtain a new access token without re-authentication.
 */
public record RefreshToken(Token value) {
}
