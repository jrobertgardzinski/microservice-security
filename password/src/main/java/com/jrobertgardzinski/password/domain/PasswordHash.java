package com.jrobertgardzinski.password.domain;

/**
 * Hashed password value object. Produced by a hash algorithm, never constructed directly.
 */
public record PasswordHash(String value) {
}
