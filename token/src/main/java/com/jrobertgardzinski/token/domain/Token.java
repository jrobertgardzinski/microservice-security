package com.jrobertgardzinski.token.domain;

import java.util.UUID;

/**
 * Base token value object. Use {@link #random()} for secure random generation.
 */
public record Token(String value) {

    public Token {
        if (value == null) {
            throw new IllegalArgumentException("Token value must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Token value must not be blank");
        }
    }

    public static Token random() {
        return new Token(UUID.randomUUID().toString());
    }
}
