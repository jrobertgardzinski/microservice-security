package com.jrobertgardzinski.token.domain;

/**
 * Short-lived access token issued after successful authentication.
 */
public record AccessToken(Token value) {
}
