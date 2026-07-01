package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.system.authorization.Authorize;
import com.jrobertgardzinski.security.system.authorization.AuthorizationResult;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Guards protected resources. Reads the {@code Authorization: Bearer <accessToken>} header, runs the
 * {@link Authorize} use case, and either lets the request through (publishing the authenticated
 * email as a request attribute for the resource to read) or rejects it with 401 — for a missing,
 * malformed, unknown or expired token alike.
 */
@ServerFilter({"/me", "/sessions/**", "/account/**"})
final class AuthorizationFilter {

    static final String AUTHENTICATED_EMAIL = "authenticatedEmail";

    private final Authorize authorize;

    AuthorizationFilter(Authorize authorize) {
        this.authorize = authorize;
    }

    @RequestFilter
    @Nullable
    HttpResponse<?> authorize(HttpRequest<?> request) {
        String token = bearerToken(request);
        if (token == null) {
            return HttpResponse.unauthorized();
        }
        if (authorize.execute(new AccessToken(token)) instanceof AuthorizationResult.Authorized authorized) {
            request.setAttribute(AUTHENTICATED_EMAIL, authorized.email().value());
            return null; // proceed to the resource
        }
        return HttpResponse.unauthorized();
    }

    private static String bearerToken(HttpRequest<?> request) {
        return request.getHeaders().getAuthorization()
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring("Bearer ".length()).trim())
                .filter(token -> !token.isEmpty())
                .orElse(null);
    }
}
