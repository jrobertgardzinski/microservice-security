package com.jrobertgardzinski.email.domain;

public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException(String rawValue, EmailPolicy policy) {
        super("Email '%s' does not satisfy policy %s"
                .formatted(rawValue, policy.getClass().getSimpleName()));
    }
}
