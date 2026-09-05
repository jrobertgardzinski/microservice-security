package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The identities a deployment declares ADMIN before any role is granted: the way the first admin
 * exists at all. Compared case-insensitively on the whole address, as the deployment writes it.
 */
public record BootstrapAdmins(Set<String> admins) {

    public BootstrapAdmins {
        admins = admins.stream().map(BootstrapAdmins::fold).collect(Collectors.toUnmodifiableSet());
    }

    public static BootstrapAdmins none() {
        return new BootstrapAdmins(Set.of());
    }

    /** Raw addresses as a deployment writes them; blanks are ignored, an invalid address is refused. */
    public static BootstrapAdmins of(Collection<String> rawAddresses) {
        return new BootstrapAdmins(rawAddresses.stream()
                .map(String::trim)
                .filter(raw -> !raw.isBlank())
                .map(Email::of)
                .map(Email::value)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean contains(Email email) {
        return admins.contains(fold(email.value()));
    }

    private static String fold(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }
}
