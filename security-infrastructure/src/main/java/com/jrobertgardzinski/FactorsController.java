package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.mfa.MfaService;
import com.jrobertgardzinski.security.domain.core.FactorType;
import com.jrobertgardzinski.security.domain.core.StepUpAction;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * Manage a signed-in user's MFA factors — the per-user half of the configuration. Enrolling proves
 * control of the new factor in two steps ({@code enroll/start} sends a challenge, {@code
 * enroll/confirm} accepts one proof). {@link AuthorizationFilter} has already authorized the caller
 * and published their e-mail; every action is against their own factors.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/factors")
final class FactorsController {

    private final MfaService mfa;
    private final StepUpGuard stepUpGuard;

    FactorsController(MfaService mfa, StepUpGuard stepUpGuard) {
        this.mfa = mfa;
        this.stepUpGuard = stepUpGuard;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> list(HttpRequest<?> request) {
        MfaService.Factors factors = mfa.factors(Caller.of(request));
        return HttpResponse.ok(Map.of(
                "have", factors.have().stream()
                        .map(held -> Map.of("type", held.type().value(), "label", held.label()))
                        .toList(),
                "offered", factors.offered().stream().map(FactorType::value).toList()));
    }

    @Post(value = "/{type}/enroll/start", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> start(HttpRequest<?> request, @PathVariable String type,
                                            @Nullable @Body Map<String, String> body) {
        return respond(mfa.startEnrolment(Caller.of(request), type, body == null ? null : body.get("target"),
                () -> stepUpGuard.requireElevation(request, StepUpAction.ENROL_FACTOR).isEmpty()),
                StepUpAction.ENROL_FACTOR);
    }

    @Post(value = "/{type}/enroll/confirm", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> confirm(HttpRequest<?> request, @PathVariable String type,
                                              @Body Map<String, String> body) {
        return respond(mfa.confirmEnrolment(Caller.of(request), type, body.get("code")), StepUpAction.ENROL_FACTOR);
    }

    @Delete(value = "/{type}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> remove(HttpRequest<?> request, @PathVariable String type) {
        return switch (mfa.removeFactor(Caller.of(request), type,
                () -> stepUpGuard.requireElevation(request, StepUpAction.REMOVE_FACTOR).isEmpty())) {
            case MfaService.Removal.Removed removed -> HttpResponse.ok(Map.of("status", "REMOVED"));
            case MfaService.Removal.WouldBreakFloor floor ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.CONFLICT)
                            .body(Map.of("status", "WOULD_BREAK_MFA_FLOOR"));
            case MfaService.Removal.UnknownFactor unknown -> HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case MfaService.Removal.StepUpRequired stepUp -> StepUpGuard.refusal(StepUpAction.REMOVE_FACTOR);
        };
    }

    private static HttpResponse<Map<String, Object>> respond(MfaService.Enrolment result, StepUpAction guarded) {
        return switch (result) {
            case MfaService.Enrolment.Started started -> {
                // a code factor sent a code (no display); a possession factor returns what to show (TOTP URI)
                Map<String, Object> body = started.display() == null
                        ? Map.of("status", "ENROLL_CODE_SENT")
                        : Map.of("status", "ENROLL_SETUP", "display", started.display());
                yield HttpResponse.accepted().body(body);
            }
            case MfaService.Enrolment.Enrolled enrolled ->
                    HttpResponse.ok(Map.of("status", "ENROLLED", "type", enrolled.type().value()));
            case MfaService.Enrolment.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE"));
            case MfaService.Enrolment.NoPendingEnrolment none ->
                    HttpResponse.<Map<String, Object>>badRequest(Map.of("status", "NO_PENDING_ENROLMENT"));
            case MfaService.Enrolment.UnsupportedFactor unsupported ->
                    HttpResponse.<Map<String, Object>>badRequest(Map.of("status", "UNSUPPORTED_FACTOR"));
            case MfaService.Enrolment.UnknownFactor unknown ->
                    HttpResponse.<Map<String, Object>>badRequest(Map.of("status", "BAD_REQUEST"));
            case MfaService.Enrolment.StepUpRequired stepUp -> StepUpGuard.refusal(guarded);
        };
    }
}
