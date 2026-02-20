package com.jrobertgardzinski.salt.domain;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic salt value object.
 * Use {@link #generate(int)} for secure random generation,
 * or the record constructor to wrap a pre-existing encoded value.
 */
public record Salt(String value) {

    public Salt {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Salt value must not be blank");
        }
    }

    public static Salt generate(int byteLength) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return new Salt(Base64.getEncoder().encodeToString(bytes));
    }
}
