package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A registered participant in the system, with the {@link Role}s they hold. Every user is a
 * {@code USER}; {@code MODERATOR} and {@code ADMIN} are grants on top. The role set is normalised
 * on construction to always include {@code USER}, so "signed in" and "is a USER" are the same thing.
 */
public record User(
        UUID id,
        Email email,
        HashedPassword passwordHash,
        NormalizedEmail normalizedEmail,
        Set<Role> roles
        ) {
    public User {
        EnumSet<Role> normalised = roles == null || roles.isEmpty()
                ? EnumSet.of(Role.USER) : EnumSet.copyOf(roles);
        normalised.add(Role.USER);
        roles = Set.copyOf(normalised);
    }

    /** A freshly registered user: a plain USER until an admin grants more. */
    public User(Email email, HashedPassword passwordHash) {
        this(UUID.randomUUID(), email, passwordHash, NormalizedEmail.of(email), Set.of(Role.USER));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
