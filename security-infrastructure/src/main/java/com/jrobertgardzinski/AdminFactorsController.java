package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-only: reset another user's MFA factors — the recovery path when someone is locked out of
 * every factor (lost phone, etc.). The caller must be an ADMIN (from a persisted role or the config
 * bootstrap) and must have just STEPPED UP, since wiping a user's factors drops them below their
 * role floor and forces re-enrolment. Same double gate as granting roles, plus step-up.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/users")
final class AdminFactorsController {

    private final EnrolledFactorRepository factors;
    private final UserRepository users;
    private final StepUpGuard stepUpGuard;
    private final TransactionBoundary transactionBoundary;
    private final Set<String> bootstrapAdmins;

    AdminFactorsController(EnrolledFactorRepository factors, UserRepository users, StepUpGuard stepUpGuard,
                           TransactionBoundary transactionBoundary,
                           @Value("${security.bootstrap-admins:}") List<String> bootstrapAdmins) {
        this.factors = factors;
        this.users = users;
        this.stepUpGuard = stepUpGuard;
        this.transactionBoundary = transactionBoundary;
        this.bootstrapAdmins = bootstrapAdmins.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Put(value = "/{email}/factors/reset", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> reset(HttpRequest<?> request, @PathVariable String email) {
        String caller = request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow();
        if (!isAdmin(caller)) {
            return HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN).body(Map.of("status", "NOT_AN_ADMIN"));
        }
        Optional<HttpResponse<Map<String, Object>>> stepUp = stepUpGuard.requireElevation(request, "admin-reset");
        if (stepUp.isPresent()) {
            return stepUp.get();
        }
        transactionBoundary.execute(() -> {
            factors.removeAll(Email.of(email));
            return null;
        });
        return HttpResponse.ok(Map.of("status", "FACTORS_RESET", "user", email));
    }

    private boolean isAdmin(String caller) {
        return bootstrapAdmins.contains(caller.toLowerCase(Locale.ROOT))
                || users.findBy(Email.of(caller)).map(user -> user.hasRole(Role.ADMIN)).orElse(false);
    }
}
