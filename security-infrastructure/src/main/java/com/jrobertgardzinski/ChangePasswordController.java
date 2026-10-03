package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountService;
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
 * HTTP entry point for changing a password. A protected endpoint: {@link AuthorizationFilter} has
 * already authorized the access token and published the caller's email, so we change that user's
 * password once their current password checks out, through
 * {@link com.jrobertgardzinski.security.application.account.AccountService}.
 *
 * <p>Authorized is not the same as unlimited. Checking the current password runs a full Argon2 and
 * answers whether the guess was right, so with a stolen access token this endpoint is a password
 * oracle — and a hit is a takeover, because changing the password revokes every session, the
 * owner's included. Hence a per-source window of its own, the same shape the anonymous endpoints
 * and the step-up already use.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/password")
final class ChangePasswordController {

    private final AccountService account;
    private final ClientIpResolver clientIpResolver;

    ChangePasswordController(AccountService account, ClientIpResolver clientIpResolver) {
        this.account = account;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> change(HttpRequest<?> request, @Body Map<String, Object> body) {
        return switch (account.changePassword(Caller.of(request), JsonBody.text(body, "currentPassword"),
                JsonBody.text(body, "newPassword"), clientIpResolver.resolve(request))) {
            case AccountService.PasswordChange.Changed changed ->
                    HttpResponse.ok(Map.of("status", "PASSWORD_CHANGED"));
            case AccountService.PasswordChange.WrongCurrentPassword wrong ->
                    HttpResponse.badRequest().body(Map.of("status", "WRONG_CURRENT_PASSWORD"));
            case AccountService.PasswordChange.WeakPassword weak ->
                    HttpResponse.badRequest().body(Map.of("status", "WEAK_PASSWORD"));
            case AccountService.PasswordChange.Incomplete incomplete ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case AccountService.PasswordChange.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
        };
    }
}
