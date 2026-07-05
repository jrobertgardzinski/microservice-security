package com.jrobertgardzinski.security.config.mfa;

import java.util.Map;
import java.util.Set;

/**
 * How many factors a role demands. A plain USER may run on one (the password); a privileged role
 * must have more — MODERATOR two, ADMIN three by default. Config, overridable per deployment
 * ({@code security.mfa.min-factors-by-role}); keyed by role name so this layer stays independent of
 * the domain enum. The count is the WHOLE chain including the first factor, so "2" = a password (or
 * a provider login) plus one more.
 */
public record MfaPolicy(Map<String, Integer> minByRole) {

    public MfaPolicy {
        minByRole = Map.copyOf(minByRole);
    }

    /** The strictest floor across the caller's roles (an unknown role asks for the USER minimum). */
    public int requiredFactorCount(Set<String> roleNames) {
        return roleNames.stream()
                .mapToInt(role -> minByRole.getOrDefault(role, 1))
                .max()
                .orElse(1);
    }

    public static MfaPolicy withDefaults() {
        return new MfaPolicy(Map.of("USER", 1, "MODERATOR", 2, "ADMIN", 3));
    }
}
