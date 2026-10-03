package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.session.SessionService;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Map;

/**
 * HTTP entry point for the caller's own sessions. A protected resource: {@link AuthorizationFilter}
 * has already authorized the access token and published the caller's email. {@code GET /sessions}
 * lists the active sessions; {@code POST /sessions/revoke-all} logs out everywhere (the presented
 * access token is itself one of the sessions revoked, so it stops working right after).
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/sessions")
final class SessionsController {

    private final SessionService sessions;

    SessionsController(SessionService sessions) {
        this.sessions = sessions;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> list(HttpRequest<?> request) {
        List<Map<String, Object>> active = sessions.list(Caller.of(request)).stream()
                .map(SessionsController::toJson)
                .toList();
        return HttpResponse.ok(Map.of("sessions", active));
    }

    @Post(value = "/revoke-all", consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> revokeAll(HttpRequest<?> request) {
        sessions.revokeAll(Caller.of(request));
        return HttpResponse.ok(Map.of("status", "ALL_SESSIONS_REVOKED"));
    }

    private static Map<String, Object> toJson(ActiveSession session) {
        return Map.of(
                "family", session.family().value().toString(),
                "expiresAt", session.refreshTokenExpiration().value().toString());
    }
}
