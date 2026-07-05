package com.jrobertgardzinski;

import com.jrobertgardzinski.security.system.authentication.ContinueAuthentication;
import com.jrobertgardzinski.security.system.authentication.ContinueAuthenticationResult;
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
 * The second (and further) steps of a multi-factor sign-in: the client presents a proof against the
 * ticket it got from {@code /authenticate}. Completing the chain returns the same session shape as a
 * single-factor sign-in (access token in the body, refresh token in the HttpOnly cookie); a wrong
 * proof reports how many tries remain; running out or an unknown ticket ends the attempt.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/authenticate/factor")
final class AuthFactorController {

    private final ContinueAuthentication continueAuthentication;
    private final RefreshCookies refreshCookies;
    private final TransactionBoundary transactionBoundary;

    AuthFactorController(ContinueAuthentication continueAuthentication, RefreshCookies refreshCookies,
                         TransactionBoundary transactionBoundary) {
        this.continueAuthentication = continueAuthentication;
        this.refreshCookies = refreshCookies;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> submit(@Body Map<String, String> body) {
        String ticket = body.get("mfaTicket");
        String proof = body.get("proof");
        ContinueAuthenticationResult result =
                transactionBoundary.execute(() -> continueAuthentication.execute(ticket, proof));
        return switch (result) {
            case ContinueAuthenticationResult.Completed completed ->
                    HttpResponse.ok(Map.<String, Object>of("accessToken", completed.session().plainAccessToken()))
                            .cookie(refreshCookies.issue(completed.session().plainRefreshToken()));
            case ContinueAuthenticationResult.NextFactor next ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(Map.of("status", "MFA_REQUIRED", "mfaTicket", ticket,
                                    "nextFactor", next.type().value()));
            case ContinueAuthenticationResult.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE", "attemptsLeft", wrong.attemptsLeft()));
            case ContinueAuthenticationResult.TooManyAttempts tooMany ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
            case ContinueAuthenticationResult.InvalidTicket invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "INVALID_OR_EXPIRED_TICKET"));
        };
    }
}
