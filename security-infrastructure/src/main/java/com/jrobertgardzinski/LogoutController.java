package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.system.session.Logout;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point for logout. Ends the session named by the refresh-token cookie (driving the same
 * {@link Logout} use case as any other entry point) and clears the cookie. Idempotent: with no
 * cookie there is nothing to end, and the response still succeeds and clears the cookie.
 */
@Controller("/logout")
final class LogoutController {

    private final Logout logout;
    private final RefreshCookies refreshCookies;
    private final TransactionBoundary transactionBoundary;

    LogoutController(Logout logout, RefreshCookies refreshCookies, TransactionBoundary transactionBoundary) {
        this.logout = logout;
        this.refreshCookies = refreshCookies;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> logout(HttpRequest<?> request) {
        refreshCookies.read(request).ifPresent(token -> transactionBoundary.execute(() -> {
            logout.execute(new RefreshToken(token));
            return null;
        }));
        return HttpResponse.ok(Map.<String, Object>of("status", "LOGGED_OUT")).cookie(refreshCookies.clear());
    }
}
