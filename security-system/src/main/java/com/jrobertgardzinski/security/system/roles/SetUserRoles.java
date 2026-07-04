package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Optional;
import java.util.Set;

/**
 * Grant or revoke a user's roles — the admin action behind flat RBAC. The target must exist; USER
 * can never be removed (it is what "signed in" means). WHO may call this is the boundary's concern
 * (only an admin); this use case is the state change and its outcome.
 */
public class SetUserRoles {

    public enum Status { UPDATED, NO_SUCH_USER }

    public record Result(Status status, Set<Role> roles) {}

    private final UserRepository users;

    public SetUserRoles(UserRepository users) {
        this.users = users;
    }

    public Result execute(Email target, Set<Role> roles) {
        Optional<User> user = users.findBy(target);
        if (user.isEmpty()) {
            return new Result(Status.NO_SUCH_USER, Set.of());
        }
        users.setRoles(target, roles);
        return new Result(Status.UPDATED, users.findBy(target).map(User::roles).orElse(Set.of(Role.USER)));
    }
}
