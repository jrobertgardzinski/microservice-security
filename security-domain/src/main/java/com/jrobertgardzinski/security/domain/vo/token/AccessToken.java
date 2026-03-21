package com.jrobertgardzinski.security.domain.vo.token;

/**
 * Short-lived access token issued after successful authentication.
 */
public record AccessToken(Token value) {
}
