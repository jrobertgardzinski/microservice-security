package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.system.session.RefreshSession;
import com.jrobertgardzinski.security.system.session.RefreshSessionResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP entry point for session refresh. Drives the same {@link RefreshSession} use case as the
 * application-level Cucumber glue — shared behaviour, different entry point. The refresh token
 * travels only in the {@code HttpOnly} cookie (see {@link RefreshCookies}); the user is found from
 * the token, so the client never names itself.
 *
 * <p>The HTTP contract:
 * <ul>
 *   <li>{@code Refreshed} &rarr; 200 OK, a new access token in the body and a rotated refresh
 *       cookie in {@code Set-Cookie}</li>
 *   <li>{@code Expired} / {@code NotFound} / no cookie &rarr; 401 Unauthorized (uniform — the
 *       response does not reveal whether the token existed)</li>
 * </ul>
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/refresh")
public class RefreshController {

    private final RefreshSession refreshSession;
    private final RefreshCookies refreshCookies;
    private final TransactionBoundary transactionBoundary;

    public RefreshController(RefreshSession refreshSession, RefreshCookies refreshCookies,
                            TransactionBoundary transactionBoundary) {
        this.refreshSession = refreshSession;
        this.refreshCookies = refreshCookies;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> refresh(HttpRequest<?> request) {
        Optional<String> presentedToken = refreshCookies.read(request);
        if (presentedToken.isEmpty()) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }

        SessionRefreshRequest refreshRequest = new SessionRefreshRequest(new RefreshToken(presentedToken.get()));
        RefreshSessionResult result = transactionBoundary.execute(() -> refreshSession.execute(refreshRequest));

        return switch (result) {
            case RefreshSessionResult.Refreshed refreshed ->
                    HttpResponse.ok(Map.<String, Object>of("accessToken", refreshed.sessionTokens().plainAccessToken()))
                            .cookie(refreshCookies.issue(refreshed.sessionTokens().plainRefreshToken()));
            case RefreshSessionResult.Expired expired ->
                    HttpResponse.status(HttpStatus.UNAUTHORIZED);
            case RefreshSessionResult.NotFound notFound ->
                    HttpResponse.status(HttpStatus.UNAUTHORIZED);
            case RefreshSessionResult.ReuseDetected reuseDetected ->
                    HttpResponse.status(HttpStatus.UNAUTHORIZED);
        };
    }
}
