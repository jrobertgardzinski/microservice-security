package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.AuthenticationResult;
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
 * HTTP entry point for authentication. Drives the same {@link Authentication} use case the
 * application-level Cucumber glue drives directly — "same behaviour, different entry point". The
 * source IP that brute-force protection keys on is resolved from the connection (see
 * {@link ClientIpResolver}), not from the request body, so a caller cannot pick its own source.
 * The User-Agent header rides along as observed context — forensics only, never part of the key.
 *
 * <p>The HTTP contract:
 * <ul>
 *   <li>{@code Authenticated}    &rarr; 200 OK, {@code {"accessToken": ..., "refreshToken": ...}}</li>
 *   <li>{@code Rejected}         &rarr; 401 Unauthorized</li>
 *   <li>{@code EmailNotVerified} &rarr; 403 Forbidden, {@code {"error": "EMAIL_NOT_VERIFIED"}}
 *       (correct credentials, but the address awaits verification)</li>
 *   <li>{@code Blocked}          &rarr; 429 Too Many Requests, with a {@code Retry-After} header
 *       (seconds until the block expires)</li>
 * </ul>
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/authenticate")
public class AuthenticationController {

    private final Authentication authentication;
    private final ClientIpResolver ipResolver;
    private final RefreshCookies refreshCookies;
    private final TransactionBoundary transactionBoundary;
    private final Clock clock;

    public AuthenticationController(Authentication authentication, ClientIpResolver ipResolver,
                                    RefreshCookies refreshCookies, TransactionBoundary transactionBoundary, Clock clock) {
        this.authentication = authentication;
        this.ipResolver = ipResolver;
        this.refreshCookies = refreshCookies;
        this.transactionBoundary = transactionBoundary;
        this.clock = clock;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> authenticate(@Body Map<String, String> body, HttpRequest<?> request) {
        Source source = new Source(ipResolver.resolve(request),
                request.getHeaders().findFirst("User-Agent").orElse(""));
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                source, Email.of(body.get("email")), PlaintextPassword.of(body.get("password")));
        AuthenticationResult result = transactionBoundary.execute(() -> authentication.execute(authenticationRequest));

        return switch (result) {
            case AuthenticationResult.Authenticated authenticated ->
                    HttpResponse.ok(Map.<String, Object>of("accessToken", authenticated.session().plainAccessToken()))
                            .cookie(refreshCookies.issue(authenticated.session().plainRefreshToken()));
            case AuthenticationResult.Rejected rejected ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED);
            case AuthenticationResult.EmailNotVerified notVerified ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "EMAIL_NOT_VERIFIED"));
            case AuthenticationResult.Blocked blocked ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", Long.toString(secondsUntil(blocked.authenticationBlock().expiryDate())))
                            .body(Map.of("error", "TOO_MANY_ATTEMPTS"));
        };
    }

    private long secondsUntil(LocalDateTime expiry) {
        return Math.max(0, Duration.between(LocalDateTime.now(clock), expiry).toSeconds());
    }
}
