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
 *
 * <p>One change is refused: the one that leaves nobody holding ADMIN. Granting roles is itself an
 * admin action, so the administrator who drops their own grant while being the last one locks the
 * door from the inside — nobody left can grant it back. The only way out is the deployment's
 * {@code security.bootstrap-admins}, which is why a deployment that names somebody there is allowed
 * to go ahead: there, an admin still exists, by configuration rather than by a row.
 */
public class SetUserRoles {

    public enum Status { UPDATED, NO_SUCH_USER, WOULD_LEAVE_NO_ADMIN }

    public record Result(Status status, Set<Role> roles) {}

    private final UserRepository users;
    private final BootstrapAdmins bootstrapAdmins;

    public SetUserRoles(UserRepository users, BootstrapAdmins bootstrapAdmins) {
        this.users = users;
        this.bootstrapAdmins = bootstrapAdmins;
    }

    public Result execute(Email target, Set<Role> roles) {
        Optional<User> user = users.findBy(target);
        if (user.isEmpty()) {
            return new Result(Status.NO_SUCH_USER, Set.of());
        }
        if (wouldLeaveNoAdmin(user.get(), roles)) {
            return new Result(Status.WOULD_LEAVE_NO_ADMIN, user.get().roles());
        }
        users.setRoles(target, roles);
        return new Result(Status.UPDATED, users.findBy(target).map(User::roles).orElse(Set.of(Role.USER)));
    }

    /**
     * Is this the change that removes the last ADMIN there is?
     *
     * <p>Only a removal can do it, and only when this account is the sole admin ROW and the
     * deployment declares no bootstrap admin. Counting rows is the repository's job; combining that
     * with what the deployment says is this use case's.
     */
    private boolean wouldLeaveNoAdmin(User user, Set<Role> roles) {
        boolean losesAdmin = user.hasRole(Role.ADMIN) && !roles.contains(Role.ADMIN);
        return losesAdmin && users.countAdmins() <= 1 && bootstrapAdmins.admins().isEmpty();
    }
}
