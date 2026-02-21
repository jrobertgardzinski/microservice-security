package com.jrobertgardzinski.password.domain;

import java.util.Optional;

/**
 * Single password rule. Returns an error message when violated, empty when satisfied.
 * Business rules (minimum length, character classes) live in password-specifications.
 */
public interface PasswordSpecification {
    Optional<String> check(PlaintextPassword password);
}
