package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.system.roles.SetUserRoles;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-only: grant or revoke another user's roles. {@link AuthorizationFilter} has already
 * authorized the caller; this controller adds the second gate — the caller must themselves be an
 * ADMIN. The first admin is bootstrapped from config ({@code security.bootstrap-admins}), which
 * breaks the chicken-and-egg: a bootstrap admin is treated as ADMIN even before any grant, and can
 * then hand out persisted roles to everyone else.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/users")
final class AdminRolesController {

    private final SetUserRoles setUserRoles;
    private final UserRepository users;
    private final Set<String> bootstrapAdmins;

    AdminRolesController(SetUserRoles setUserRoles, UserRepository users,
                         @Value("${security.bootstrap-admins:}") List<String> bootstrapAdmins) {
        this.setUserRoles = setUserRoles;
        this.users = users;
        this.bootstrapAdmins = bootstrapAdmins.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Put(value = "/{email}/roles", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> setRoles(HttpRequest<?> request, @PathVariable String email, @Body Map<String, Object> body) {
        String caller = request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow();
        if (!isAdmin(caller)) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "NOT_AN_ADMIN"));
        }
        Set<Role> roles;
        try {
            roles = parseRoles(body.get("roles"));
        } catch (IllegalArgumentException unknownRole) {
            return HttpResponse.badRequest(Map.of("status", "UNKNOWN_ROLE", "detail", unknownRole.getMessage()));
        }
        SetUserRoles.Result result = setUserRoles.execute(Email.of(email), roles);
        if (result.status() == SetUserRoles.Status.NO_SUCH_USER) {
            return HttpResponse.notFound(Map.of("status", "NO_SUCH_USER"));
        }
        return HttpResponse.ok(Map.of("email", email,
                "roles", result.roles().stream().map(Role::name).sorted().toList()));
    }

    private boolean isAdmin(String email) {
        if (bootstrapAdmins.contains(email.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return users.findBy(Email.of(email)).map(u -> u.hasRole(Role.ADMIN)).orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static Set<Role> parseRoles(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("roles must be a list");
        }
        return ((List<Object>) list).stream()
                .map(o -> Role.valueOf(String.valueOf(o).trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }
}
