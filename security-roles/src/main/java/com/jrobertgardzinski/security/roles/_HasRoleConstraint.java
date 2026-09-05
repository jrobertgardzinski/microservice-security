package com.jrobertgardzinski.security.roles;

import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.util.constraint.ErrorConstraint;

import java.util.Set;

class _HasRoleConstraint extends ErrorConstraint<Set<Role>> {

    private final Role required;

    _HasRoleConstraint(Role required) {
        this.required = required;
    }

    @Override
    public boolean isSatisfied(Set<Role> roles) {
        return roles.contains(required);
    }

    @Override
    public String code() {
        return switch (required) {
            case ADMIN -> "NOT_AN_ADMIN";
            case MODERATOR -> "NOT_A_MODERATOR";
            case USER -> "NOT_A_USER";
        };
    }
}
