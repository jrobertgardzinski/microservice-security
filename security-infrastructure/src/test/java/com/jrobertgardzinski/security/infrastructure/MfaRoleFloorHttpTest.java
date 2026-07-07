package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.CapturingEmailCodeChannel;
import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MFA role floor. A plain USER runs on the password alone. Granting MODERATOR raises the floor
 * to two factors: the account is now under-protected — {@code /me} reports it, the sign-in still
 * works but every sensitive endpoint answers 403 {@code MFA_ENROLMENT_REQUIRED} until a second
 * factor is enrolled, while {@code /me} and the enrolment endpoints stay open so the user can
 * actually comply. A bootstrap admin is graced until it enrols its first factor — otherwise the
 * first admin could never sign in to grant anything.
 */
@Epic("Authentication")
@Feature("MFA role floor")
class MfaRoleFloorHttpTest {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String ADMIN = "admin@example.com";   // the test env's bootstrap admin

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a promoted user is boxed to enrolment until it meets its role's floor")
    void promoted_user_must_enrol_to_regain_access() {
        String user = "mod-to-be@example.com";
        String userToken = onboard(user);
        String adminToken = onboard(ADMIN);   // bootstrap admin, graced while it has no factors

        // as a USER the password is enough — a sensitive endpoint works and /me is compliant
        assertEquals(HttpStatus.OK, get("/sessions", userToken).getStatus());
        assertEquals(true, me(userToken).get("mfaCompliant"));

        // the bootstrap admin (graced) can still reach /admin and grants MODERATOR
        HttpResponse<Map> granted = exchange(HttpRequest.PUT("/admin/users/" + user + "/roles",
                Map.of("roles", List.of("MODERATOR"))).header("Authorization", "Bearer " + adminToken));
        assertEquals(HttpStatus.OK, granted.getStatus());

        // now the floor is 2 and the user has one factor (the password): under-protected
        Map<?, ?> nowMe = me(userToken);
        assertEquals(false, nowMe.get("mfaCompliant"));
        // a token minted NOW carries the compliance verdict as a claim, so offline consumers
        // (comments and friends) can withhold privileged roles without calling /me
        assertEquals(false, claims(signIn(user)).get("mfaCompliant"),
                "a fresh token of an under-enrolled moderator says mfaCompliant=false");
        assertEquals(2, nowMe.get("requiredFactors"));
        assertEquals(1, nowMe.get("haveFactors"));

        // sensitive endpoints are shut, but /me and enrolment stay open
        assertEquals(HttpStatus.FORBIDDEN, get("/sessions", userToken).getStatus());
        assertEquals(HttpStatus.OK, get("/account/factors", userToken).getStatus());

        // enrol the e-mail factor → compliant again, and the sensitive endpoint reopens
        enrolEmailFactor(user, userToken);
        assertEquals(true, me(userToken).get("mfaCompliant"));
        assertEquals(HttpStatus.OK, get("/sessions", userToken).getStatus());

        // and the floor cannot be undercut: dropping back to one factor is refused
        HttpResponse<Map> removal = exchange(HttpRequest.DELETE("/account/factors/EMAIL_CODE")
                .header("Authorization", "Bearer " + userToken));
        assertEquals(HttpStatus.CONFLICT, removal.getStatus());
        assertEquals("WOULD_BREAK_MFA_FLOOR", removal.getBody(Map.class).orElseThrow().get("status"));
    }

    // --- Helpers --------------------------------------------------------------

    private String onboard(String email) {
        assertTrue(List.of(HttpStatus.CREATED).contains(
                exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD))).getStatus()));
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken)));
        return (String) exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)))
                .getBody(Map.class).orElseThrow().get("accessToken");
    }

    /** A fresh token for an already-onboarded account (re-authenticate). */
    private String signIn(String email) {
        return (String) exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)))
                .getBody(Map.class).orElseThrow().get("accessToken");
    }

    /** The JWT payload, decoded — what an offline consumer reads. */
    private Map<?, ?> claims(String jwt) {
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]);
            return server.getApplicationContext().getBean(io.micronaut.json.JsonMapper.class)
                    .readValue(payload, Map.class);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void enrolEmailFactor(String email, String token) {
        exchange(HttpRequest.POST("/account/factors/EMAIL_CODE/enroll/start", Map.of())
                .header("Authorization", "Bearer " + token));
        String code = server.getApplicationContext().getBean(CapturingEmailCodeChannel.class).lastCodeFor(email);
        HttpResponse<Map> confirmed = exchange(HttpRequest.POST("/account/factors/EMAIL_CODE/enroll/confirm",
                Map.of("code", code)).header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.OK, confirmed.getStatus());
    }

    private Map<?, ?> me(String token) {
        return exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + token))
                .getBody(Map.class).orElseThrow();
    }

    private HttpResponse<Map> get(String path, String token) {
        return exchange(HttpRequest.GET(path).header("Authorization", "Bearer " + token));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<Map>) e.getResponse();
        }
    }
}
