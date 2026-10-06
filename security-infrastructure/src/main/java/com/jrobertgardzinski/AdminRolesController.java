package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.core.AdminService;
import com.jrobertgardzinski.security.domain.core.Role;
import com.jrobertgardzinski.security.domain.core.StepUpAction;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Admin-only: grant or revoke another user's roles. {@link AuthorizationFilter} has already
 * authorized the caller; {@link RoleGuard} adds the second gate — the caller must themselves be an
 * ADMIN, from a persisted grant or the deployment's bootstrap list, which breaks the
 * chicken-and-egg: a bootstrap admin is ADMIN before any grant and can hand out roles to everyone else.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/users")
final class AdminRolesController {

    private final AdminService admin;
    private final RoleGuard roleGuard;
    private final StepUpGuard stepUpGuard;

    AdminRolesController(AdminService admin, RoleGuard roleGuard, StepUpGuard stepUpGuard) {
        this.admin = admin;
        this.roleGuard = roleGuard;
        this.stepUpGuard = stepUpGuard;
    }

    @Put(value = "/{email}/roles", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> setRoles(HttpRequest<?> request, @PathVariable String email, @Body Map<String, Object> body) {
        // the role gate first, so a non-admin learns only that they are not an admin
        Optional<HttpResponse<Map<String, Object>>> notAnAdmin = roleGuard.require(request, Role.ADMIN);
        if (notAnAdmin.isPresent()) {
            return notAnAdmin.get();
        }
        return switch (admin.setRoles(email, body.get("roles"),
                () -> stepUpGuard.requireElevation(request, StepUpAction.ADMIN_ROLES).isEmpty())) {
            case AdminService.RolesChange.Updated updated ->
                    HttpResponse.ok(Map.of("email", email, "roles", names(updated.roles())));
            case AdminService.RolesChange.NoSuchUser none -> HttpResponse.notFound(Map.of("status", "NO_SUCH_USER"));
            // 409, not 403: the caller IS allowed to do this, and the state of the system is what
            // refuses. Telling them which is the difference between "try again with proof" and
            // "grant somebody else first".
            case AdminService.RolesChange.WouldLeaveNoAdmin noAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.CONFLICT)
                            .body(Map.of("status", "WOULD_LEAVE_NO_ADMIN", "roles", names(noAdmin.roles())));
            case AdminService.RolesChange.InvalidEmail invalid ->
                    HttpResponse.badRequest(Map.of("status", "INVALID_EMAIL"));
            case AdminService.RolesChange.UnknownRole unknown ->
                    HttpResponse.badRequest(Map.of("status", "UNKNOWN_ROLE", "roles", unknown.known()));
            case AdminService.RolesChange.StepUpRequired stepUp -> StepUpGuard.refusal(StepUpAction.ADMIN_ROLES);
        };
    }

    private static java.util.List<String> names(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().toList();
    }
}
