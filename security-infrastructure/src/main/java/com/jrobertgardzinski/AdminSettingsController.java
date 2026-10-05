package com.jrobertgardzinski;

import com.jrobertgardzinski.config.ConfigValue;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.security.application.core.AdminService;
import com.jrobertgardzinski.security.domain.core.Role;
import com.jrobertgardzinski.security.domain.mfa.StepUpAction;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Admin-only: every rule declared live, by its key. GET lists the catalogue with what is in force
 * under each key and its provenance - the level that answered and what was refused on the way.
 * PUT tells one rule a value by its key: the catalogue supplies the rule's own parser and gate,
 * so there is no code per rule and no rule an ADMIN can reach that the system does not read.
 * The value arrives as text, the way a property or a row holds it; a number or a flag in the
 * JSON is read as its text. A key nobody declared is 404, a value the rule refuses is 400 with
 * the gate's reason, and in both cases nothing is written. The caller must be an ADMIN, and
 * every write takes a fresh step-up, since a rule binds every future decision under it.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/settings")
final class AdminSettingsController {

    static final StepUpAction STEP_UP_ACTION = StepUpAction.ADMIN_SETTINGS;

    private final AdminService admin;
    private final RoleGuard roleGuard;
    private final StepUpGuard stepUpGuard;

    AdminSettingsController(AdminService admin, RoleGuard roleGuard, StepUpGuard stepUpGuard) {
        this.admin = admin;
        this.roleGuard = roleGuard;
        this.stepUpGuard = stepUpGuard;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> catalogue(HttpRequest<?> request) {
        Optional<HttpResponse<Map<String, Object>>> notAnAdmin = roleGuard.require(request, Role.ADMIN);
        if (notAnAdmin.isPresent()) {
            return notAnAdmin.get();
        }
        return HttpResponse.ok(report(admin.settings()));
    }

    @Put(value = "/{key}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> set(HttpRequest<?> request, @PathVariable String key, @Body Map<String, Object> body) {
        Optional<HttpResponse<Map<String, Object>>> notAnAdmin = roleGuard.require(request, Role.ADMIN);
        if (notAnAdmin.isPresent()) {
            return notAnAdmin.get();
        }
        return switch (admin.setSetting(key, body.get("value"),
                () -> stepUpGuard.requireElevation(request, STEP_UP_ACTION).isEmpty())) {
            case AdminService.SettingChange.Accepted accepted ->
                    HttpResponse.ok(Map.of("status", "ACCEPTED", "key", key, "value", accepted.value()));
            case AdminService.SettingChange.Refused refused ->
                    HttpResponse.badRequest(Map.of("status", "REFUSED", "key", key, "reason", refused.reason()));
            case AdminService.SettingChange.UnknownKey unknown -> HttpResponse.<Map<String, Object>>status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "UNKNOWN_KEY", "key", key, "reason", unknown.reason()));
            case AdminService.SettingChange.NoValue none -> HttpResponse.badRequest(Map.of("status", "NO_VALUE"));
            // the catalogue reads every value as text; only the minimum length parses one itself
            case AdminService.SettingChange.NotANumber notANumber -> HttpResponse.badRequest(Map.of("status", "NOT_A_NUMBER"));
            case AdminService.SettingChange.StepUpRequired stepUp -> StepUpGuard.refusal(STEP_UP_ACTION);
        };
    }

    /** Each rule's value, the level that answered, and what was refused on the way. */
    static Map<String, Object> report(Map<String, Resolution<? extends ConfigValue<?>>> resolutions) {
        Map<String, Object> report = new LinkedHashMap<>();
        resolutions.forEach((key, resolution) -> report.put(key, Map.of(
                "value", resolution.value().value(),
                "source", resolution.source(),
                "rejected", resolution.rejected().stream()
                        .map(r -> Map.<String, Object>of("source", r.source(), "value", r.value(), "reason", r.reason()))
                        .toList())));
        return report;
    }
}
