package com.jrobertgardzinski.security.domain.vo;

import java.security.SecureRandom;
import java.util.Base64;

public record Salt(String value) {
    public Salt {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Salt cannot be null or blank");
        }
    }

    public static Salt generate(int byteLength) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return new Salt(Base64.getEncoder().encodeToString(bytes));
    }
}
