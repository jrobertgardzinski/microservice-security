package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.system.session.RevokeAllSessions;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point for "log out everywhere". A protected endpoint: {@link AuthorizationFilter} has
 * already authorized the access token and published the caller's email, so here we revoke every
 * session that email holds via the {@link RevokeAllSessions} use case. The presented access token
 * is itself one of the sessions being revoked, so it stops working right after.
 */
@Controller("/sessions")
final class SessionsController {

    private final RevokeAllSessions revokeAllSessions;
    private final TransactionBoundary transactionBoundary;

    SessionsController(RevokeAllSessions revokeAllSessions, TransactionBoundary transactionBoundary) {
        this.revokeAllSessions = revokeAllSessions;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(value = "/revoke-all", consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> revokeAll(HttpRequest<?> request) {
        String email = request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow();
        transactionBoundary.execute(() -> {
            revokeAllSessions.execute(Email.of(email));
            return null;
        });
        return HttpResponse.ok(Map.of("status", "ALL_SESSIONS_REVOKED"));
    }
}
