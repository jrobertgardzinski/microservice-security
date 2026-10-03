package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.authentication.AuthenticationService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * HTTP entry point for authentication: the body's credentials, the caller's address and its
 * User-Agent go to {@link AuthenticationService}, its outcome comes back as a response. The source
 * IP that brute-force protection keys on is resolved from the connection (see
 * {@link ClientIpResolver}), not from the request body, so a caller cannot pick its own source.
 *
 * <p>The HTTP contract:
 * <ul>
 *   <li>{@code Authenticated}    &rarr; 200 OK, {@code {"accessToken": ...}} — plus the refresh
 *       token in a {@code Set-Cookie}, HttpOnly, and <b>never in the body</b>. This line used to
 *       promise {@code refreshToken} in the JSON, which the code has never sent: a client author
 *       implementing from the contract read null and built a refresh flow that could not work,
 *       while the mechanism that does work (a credentialed cookie, and therefore
 *       {@code credentials: 'include'} on the fetch and {@code allow-credentials} on the CORS
 *       configuration — the whole of the 2026-07-29 sign-in outage) went undocumented. Described
 *       the way {@code RefreshController} already describes its own rotated cookie.</li>
 *   <li>{@code Rejected}         &rarr; 401 Unauthorized; {@code Unreadable} (a credential the
 *       domain cannot construct) the same 401 without a body</li>
 *   <li>{@code EmailNotVerified} &rarr; 403 Forbidden, {@code {"error": "EMAIL_NOT_VERIFIED"}}
 *       (correct credentials, but the address awaits verification)</li>
 *   <li>{@code Blocked}          &rarr; 429 Too Many Requests, with a {@code Retry-After} header
 *       (seconds until the block expires); {@code Throttled} (too many attempts from one
 *       source) the same, with the throttle's window</li>
 * </ul>
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/authenticate")
public class AuthenticationController {

    private final AuthenticationService authentication;
    private final ClientIpResolver ipResolver;
    private final RefreshCookies refreshCookies;
    private final Clock clock;

    public AuthenticationController(AuthenticationService authentication, ClientIpResolver ipResolver,
                                    RefreshCookies refreshCookies, Clock clock) {
        this.authentication = authentication;
        this.ipResolver = ipResolver;
        this.refreshCookies = refreshCookies;
        this.clock = clock;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> authenticate(@Body Map<String, String> body, HttpRequest<?> request) {
        AuthenticationService.Outcome outcome = authentication.authenticate(body.get("email"), body.get("password"),
                ipResolver.resolve(request), request.getHeaders().findFirst("User-Agent").orElse(""));

        return switch (outcome) {
            case AuthenticationService.Outcome.Authenticated authenticated ->
                    HttpResponse.ok(Map.<String, Object>of("accessToken", authenticated.session().plainAccessToken()))
                            .cookie(refreshCookies.issue(authenticated.session().plainRefreshToken()));
            case AuthenticationService.Outcome.Rejected rejected ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Refusal.of("WRONG_CREDENTIALS"));
            // an address that cannot exist owns no account: the same 401, and no more said
            case AuthenticationService.Outcome.Unreadable unreadable -> HttpResponse.status(HttpStatus.UNAUTHORIZED);
            case AuthenticationService.Outcome.EmailNotVerified notVerified ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Refusal.alsoAsError("EMAIL_NOT_VERIFIED"));
            case AuthenticationService.Outcome.Blocked blocked ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", Long.toString(secondsUntil(blocked.expiry())))
                            .body(Refusal.alsoAsError("TOO_MANY_ATTEMPTS"));
            // password was right, but the user has factors — no session yet; the first challenge is out.
            // The refresh token is withheld until the chain completes (see AuthFactorController).
            case AuthenticationService.Outcome.MfaRequired mfa ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(MfaBody.of(mfa.ticket(), mfa.nextFactor().value(), mfa.challengeData()));
            case AuthenticationService.Outcome.Throttled throttled ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Refusal.alsoAsError("TOO_MANY_ATTEMPTS"));
        };
    }

    private long secondsUntil(LocalDateTime expiry) {
        return Math.max(0, Duration.between(LocalDateTime.now(clock), expiry).toSeconds());
    }
}
