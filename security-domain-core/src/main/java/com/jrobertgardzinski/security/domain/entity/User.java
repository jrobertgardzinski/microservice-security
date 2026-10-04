package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * A registered participant in the system, with the {@link Role}s they hold. Every user is a
 * {@code USER}; {@code MODERATOR} and {@code ADMIN} are grants on top. The role set is normalised
 * on construction to always include {@code USER}, so "signed in" and "is a USER" are the same thing.
 *
 * <p>{@code normalizedEmail} is normalised on construction too — from the address, whatever the
 * caller passes. It stays a component because it is what the row holds and what every lookup asks
 * by; it is not a value anybody supplies.
 */
public record User(
        UserId id,
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
        // The normalized form is DERIVED, never accepted. It is the identity this account is found
        // by — the unique index and every lookup use it — so a User carrying one that does not
        // match its own address is an account that exists and cannot be signed into. Every
        // construction site passed a consistent value and nothing made them; deriving it here means
        // nothing has to.
        normalizedEmail = NormalizedEmail.of(email);
    }

    /** A freshly registered user: a plain USER until an admin grants more. */
    public User(Email email, HashedPassword passwordHash) {
        this(UserId.random(), email, passwordHash, NormalizedEmail.of(email), Set.of(Role.USER));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
