package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;

import java.util.Optional;
import java.util.UUID;

/**
 * A registered participant in the system.
 */
public record User(
        UUID id,
        Email email,
        HashedPassword passwordHash,
        Optional<NormalizedEmail> normalizedEmail
        ) {
    public User(Email email, HashedPassword passwordHash) {
        this(UUID.randomUUID(), email, passwordHash, NormalizedEmail.optionalOf(email));
    }
}
