package com.jrobertgardzinski;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.system.settings.SetMinPasswordLength;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-only: the minimum password length as a live decision. POST sets it — through the use
 * case, so the value object is the only gate and a refused length changes nothing. GET reports
 * the value IN FORCE together with its provenance: which rung of the ladder answered and which
 * rungs were refused on the way, each with the gate's own words. That report is how an admin
 * learns that the row someone wrote at the database console is not the value the system uses.
 * Same double gate as the other admin surfaces — the caller must be an ADMIN — and setting the
 * policy takes a fresh step-up, since it widens or narrows what every future password must be.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/settings/password")
final class AdminPasswordPolicyController {

    static final String STEP_UP_ACTION = "admin-settings";

    private final SetMinPasswordLength setMinPasswordLength;
    private final ConfigLadder<Integer> minPasswordLength;
    private final UserRepository users;
    private final StepUpGuard stepUpGuard;
    private final Set<String> bootstrapAdmins;

    AdminPasswordPolicyController(SetMinPasswordLength setMinPasswordLength,
                                  ConfigLadder<Integer> minPasswordLength,
                                  UserRepository users, StepUpGuard stepUpGuard,
                                  @Value("${security.bootstrap-admins:}") List<String> bootstrapAdmins) {
        this.setMinPasswordLength = setMinPasswordLength;
        this.minPasswordLength = minPasswordLength;
        this.users = users;
        this.stepUpGuard = stepUpGuard;
        this.bootstrapAdmins = bootstrapAdmins.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Get(value = "/min-length", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> report(HttpRequest<?> request) {
        if (!isAdmin(callerOf(request))) {
            return notAnAdmin();
        }
        return HttpResponse.ok(report(minPasswordLength.resolution()));
    }

    @Post(value = "/min-length", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> set(HttpRequest<?> request, @Body Map<String, Object> body) {
        if (!isAdmin(callerOf(request))) {
            return notAnAdmin();
        }
        Optional<HttpResponse<Map<String, Object>>> stepUp = stepUpGuard.requireElevation(request, STEP_UP_ACTION);
        if (stepUp.isPresent()) {
            return stepUp.get();
        }
        int requested;
        try {
            requested = Integer.parseInt(String.valueOf(body.get("value")).trim());
        } catch (NumberFormatException notANumber) {
            return HttpResponse.badRequest(Map.of("status", "NOT_A_NUMBER"));
        }
        SetMinPasswordLength.Result result = setMinPasswordLength.execute(requested);
        if (result.status() == SetMinPasswordLength.Status.REFUSED) {
            return HttpResponse.badRequest(Map.of("status", "REFUSED", "reason", result.reason()));
        }
        return HttpResponse.ok(Map.of("status", "ACCEPTED", "value", result.minLength().value()));
    }

    private static Map<String, Object> report(Resolution<Integer> resolution) {
        return Map.of(
                "value", resolution.value(),
                "source", resolution.source(),
                "rejected", resolution.rejected().stream()
                        .map(r -> Map.<String, Object>of("source", r.source(), "value", r.value(), "reason", r.reason()))
                        .toList());
    }

    private static String callerOf(HttpRequest<?> request) {
        return request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow();
    }

    private static HttpResponse<Map<String, Object>> notAnAdmin() {
        return HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN).body(Map.of("status", "NOT_AN_ADMIN"));
    }

    private boolean isAdmin(String email) {
        if (bootstrapAdmins.contains(email.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return users.findBy(Email.of(email)).map(u -> u.hasRole(Role.ADMIN)).orElse(false);
    }
}
