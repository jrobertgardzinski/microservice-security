package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.system.mfa.StepUp;
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

    private final StepUp stepUp;

    StepUpController(StepUp stepUp) {
        this.stepUp = stepUp;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> start(HttpRequest<?> request, @Body Map<String, String> body) {
        Email email = Email.of(request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
        String token = StepUpGuard.bearerToken(request);
        return respond(stepUp.start(email, body.getOrDefault("action", ""), token, body.get("password")));
    }

    @Post(value = "/factor", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> factor(@Body Map<String, String> body) {
        return respond(stepUp.submitFactor(body.get("stepUpTicket"), body.get("proof")));
    }

    private static HttpResponse<Map<String, Object>> respond(StepUp.Result result) {
        return switch (result) {
            case StepUp.Result.Elevated elevated ->
                    HttpResponse.ok(Map.of("status", "ELEVATED"));
            case StepUp.Result.FactorRequired next ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(Map.of("status", "FACTOR_REQUIRED",
                                    "stepUpTicket", next.ticket(), "nextFactor", next.nextFactor().value()));
            case StepUp.Result.WrongPassword wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_PASSWORD"));
            case StepUp.Result.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE", "attemptsLeft", wrong.attemptsLeft()));
            case StepUp.Result.TooManyAttempts tooMany ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
            case StepUp.Result.InvalidTicket invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "INVALID_TICKET"));
        };
    }
}
