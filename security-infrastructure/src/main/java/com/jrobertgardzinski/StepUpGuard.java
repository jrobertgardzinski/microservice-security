package com.jrobertgardzinski;

import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;

/**
 * The step-up gate a sensitive endpoint puts in front of itself: the caller's access token must
 * carry a fresh, one-shot elevation (minted by {@code /account/step-up}). No elevation → 403
 * {@code STEP_UP_REQUIRED}, and the caller is told which action to step up for.
 */
@Singleton
final class StepUpGuard {

    private final SessionElevation elevation;

    StepUpGuard(SessionElevation elevation) {
        this.elevation = elevation;
    }

    /** A 403 response if the caller has not stepped up, otherwise empty (proceed). */
    Optional<HttpResponse<Map<String, Object>>> requireElevation(HttpRequest<?> request, String action) {
        String token = bearerToken(request);
        if (token != null && elevation.consume(token)) {
            return Optional.empty();
        }
        return Optional.of(HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "STEP_UP_REQUIRED", "action", action)));
    }

    static String bearerToken(HttpRequest<?> request) {
        return request.getHeaders().getAuthorization()
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring("Bearer ".length()).trim())
                .filter(token -> !token.isEmpty())
                .orElse(null);
    }
}
