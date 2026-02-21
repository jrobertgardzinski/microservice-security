package com.jrobertgardzinski.password.domain;

import java.util.List;
import java.util.Optional;

/**
 * Aggregates password specifications and validates a plaintext password against them.
 * Returns all violated rules at once so the user can fix everything in one go.
 */
public interface PasswordPolicy {

    List<PasswordSpecification> specifications();

    default List<String> validate(PlaintextPassword password) {
        return specifications().stream()
                .map(spec -> spec.check(password))
                .flatMap(Optional::stream)
                .toList();
    }
}
