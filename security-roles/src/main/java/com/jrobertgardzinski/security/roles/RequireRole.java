package com.jrobertgardzinski.security.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.util.constraint.Constraints;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RequireRole {

    private final BootstrapAdmins bootstrapAdmins;
    private final RolesOf rolesOf;

    public RequireRole(BootstrapAdmins bootstrapAdmins, RolesOf rolesOf) {
        this.bootstrapAdmins = bootstrapAdmins;
        this.rolesOf = rolesOf;
    }

    public Outcome<Set<Role>> check(Email caller, Role required) {
        return new Constraints<>(List.of(new _HasRoleConstraint(required)))
                .validate(() -> rolesInForce(caller));
    }

    public Set<Role> rolesInForce(Email caller) {
        Set<Role> roles = new HashSet<>(rolesOf.of(caller));
        if (bootstrapAdmins.contains(caller)) {
            roles.add(Role.ADMIN);
        }
        return Set.copyOf(roles);
    }
}
