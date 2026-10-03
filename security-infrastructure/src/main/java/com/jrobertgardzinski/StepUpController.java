package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.mfa.StepUpService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * Step-up authentication: re-prove yourself for a sensitive action. {@code POST /account/step-up}
 * begins it (the per-action policy decides whether a password and/or factors are needed);
 * {@code POST /account/step-up/factor} walks the factor chain. Passing it elevates the caller's
 * access token for a short window, which the sensitive endpoint then consumes. {@link
 * AuthorizationFilter} has already authorized the caller and published the email.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/step-up")
final class StepUpController {

    private final StepUpService stepUp;
    private final ClientIpResolver clientIpResolver;

    StepUpController(StepUpService stepUp, ClientIpResolver clientIpResolver) {
        this.stepUp = stepUp;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> start(HttpRequest<?> request, @Body Map<String, String> body) {
        return respond(stepUp.start(publishedCaller(request), clientIpResolver.resolve(request),
                body.get("action"), StepUpGuard.bearerToken(request), JsonBody.text(body, "password")));
    }

    @Post(value = "/factor", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> factor(HttpRequest<?> request, @Body Map<String, String> body) {
        return respond(stepUp.submitFactor(publishedCaller(request), clientIpResolver.resolve(request),
                JsonBody.text(body, "stepUpTicket"), JsonBody.text(body, "proof")));
    }

    /** The caller the filter published, or null when there is none — the service then keys on the address. */
    private static Email publishedCaller(HttpRequest<?> request) {
        try {
            return Caller.of(request);
        } catch (RuntimeException noPublishedCaller) {
            return null;
        }
    }

    private static HttpResponse<Map<String, Object>> respond(StepUpService.Outcome outcome) {
        return switch (outcome) {
            case StepUpService.Outcome.Elevated elevated ->
                    HttpResponse.ok(Map.of("status", "ELEVATED"));
            case StepUpService.Outcome.FactorRequired next ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(MfaBody.stepUp(next.ticket(), next.nextFactor().value(), next.challengeData()));
            case StepUpService.Outcome.WrongPassword wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_PASSWORD"));
            case StepUpService.Outcome.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE", "attemptsLeft", wrong.attemptsLeft()));
            case StepUpService.Outcome.TooManyAttempts tooMany ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
            case StepUpService.Outcome.InvalidTicket invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "INVALID_TICKET"));
            // 409, not 401: nothing the caller can type would help
            case StepUpService.Outcome.NothingToProveWith nothing ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.CONFLICT)
                            .body(Map.of("status", "ENROL_A_FACTOR_FIRST"));
            case StepUpService.Outcome.UnknownAction unknown ->
                    HttpResponse.badRequest(Map.of("status", "UNKNOWN_ACTION"));
            case StepUpService.Outcome.Incomplete incomplete ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case StepUpService.Outcome.NotAuthenticated notAuthenticated ->
                    HttpResponse.status(HttpStatus.UNAUTHORIZED);
            case StepUpService.Outcome.Throttled throttled ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Map.of("status", "TOO_MANY_STEP_UP_ATTEMPTS"));
        };
    }
}
