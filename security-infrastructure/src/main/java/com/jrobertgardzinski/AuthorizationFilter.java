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
 *
 * <p>It also enforces the MFA role floor: a caller whose roles demand more factors than they have
 * (see {@link com.jrobertgardzinski.security.system.mfa.MfaCompliance}) is let through only to the
 * enrolment endpoints and {@code /me} — everything else answers 403 {@code MFA_ENROLMENT_REQUIRED}
 * until they comply. The session is real; it is just boxed to becoming compliant.
 */
@ServerFilter({"/me", "/sessions", "/sessions/**", "/account/**", "/admin/**"})
final class AuthorizationFilter {

    static final String AUTHENTICATED_EMAIL = "authenticatedEmail";

    private final Authorize authorize;
    private final com.jrobertgardzinski.security.domain.repository.UserRepository users;
    private final com.jrobertgardzinski.security.system.mfa.MfaCompliance compliance;

    AuthorizationFilter(Authorize authorize,
                        com.jrobertgardzinski.security.domain.repository.UserRepository users,
                        com.jrobertgardzinski.security.system.mfa.MfaCompliance compliance) {
        this.authorize = authorize;
        this.users = users;
        this.compliance = compliance;
    }

    @RequestFilter
    @Nullable
    HttpResponse<?> authorize(HttpRequest<?> request) {
        String token = bearerToken(request);
        if (token == null) {
            return HttpResponse.unauthorized();
        }
        if (!(authorize.execute(new AccessToken(token)) instanceof AuthorizationResult.Authorized authorized)) {
            return HttpResponse.unauthorized();
        }
        request.setAttribute(AUTHENTICATED_EMAIL, authorized.email().value());
        if (!enrolmentExempt(request.getPath()) && !isCompliant(authorized.email())) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", "MFA_ENROLMENT_REQUIRED"));
        }
        return null; // proceed to the resource
    }

    /** /me and the factor-enrolment endpoints stay open so an under-enrolled user can become compliant. */
    private static boolean enrolmentExempt(String path) {
        return path.equals("/me") || path.startsWith("/account/factors");
    }

    private boolean isCompliant(com.jrobertgardzinski.email.domain.Email email) {
        java.util.Set<com.jrobertgardzinski.security.domain.vo.Role> roles = users.findBy(email)
                .map(com.jrobertgardzinski.security.domain.entity.User::roles)
                .orElse(java.util.Set.of(com.jrobertgardzinski.security.domain.vo.Role.USER));
        return compliance.isCompliant(email, roles);
    }

    private static String bearerToken(HttpRequest<?> request) {
        return request.getHeaders().getAuthorization()
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring("Bearer ".length()).trim())
                .filter(token -> !token.isEmpty())
                .orElse(null);
    }
}
