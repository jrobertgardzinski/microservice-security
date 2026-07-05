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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Admin factor reset — the recovery path when a user is locked out of every factor. Only an ADMIN,
 * only after stepping up, and the effect is real: the target's factors are wiped, so a promoted
 * user who had complied is under its floor again until it re-enrols.
 */
@Epic("Authentication")
@Feature("Admin factor reset")
class AdminFactorResetHttpTest {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String ADMIN = "admin@example.com";

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
    @DisplayName("a stepped-up admin resets a locked-out user's factors; a non-admin cannot")
    void admin_resets_factors() {
        String user = "locked-out@example.com";
        String userToken = onboard(user);
        enrolEmailFactor(user, userToken);
        assertEquals(1, haveEnrolled(userToken), "the user has one enrolled factor");

        String adminToken = onboard(ADMIN);

        // a non-admin cannot reset anyone
        assertEquals(HttpStatus.FORBIDDEN, exchange(HttpRequest.PUT("/admin/users/" + user + "/factors/reset", null)
                .header("Authorization", "Bearer " + userToken)).getStatus());

        // the admin must step up first
        assertEquals(HttpStatus.FORBIDDEN, exchange(HttpRequest.PUT("/admin/users/" + user + "/factors/reset", null)
                .header("Authorization", "Bearer " + adminToken)).getStatus());
        // bootstrap admin has no factors → step-up elevates on the (SECOND_FACTORS) start alone
        assertEquals(HttpStatus.OK, exchange(HttpRequest.POST("/account/step-up", Map.of("action", "admin-reset"))
                .header("Authorization", "Bearer " + adminToken)).getStatus());

        HttpResponse<Map> reset = exchange(HttpRequest.PUT("/admin/users/" + user + "/factors/reset", null)
                .header("Authorization", "Bearer " + adminToken));
        assertEquals(HttpStatus.OK, reset.getStatus());
        assertEquals("FACTORS_RESET", reset.getBody(Map.class).orElseThrow().get("status"));

        // the user's factor is gone
        assertEquals(0, haveEnrolled(userToken), "no enrolled factors remain after the reset");
    }

    private int haveEnrolled(String token) {
        return ((java.util.List<?>) get("/account/factors", token).getBody(Map.class).orElseThrow().get("have")).size();
    }

    // --- Helpers --------------------------------------------------------------

    private String onboard(String email) {
        exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD)));
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken)));
        return (String) exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)))
                .getBody(Map.class).orElseThrow().get("accessToken");
    }

    private void enrolEmailFactor(String email, String token) {
        exchange(HttpRequest.POST("/account/factors/EMAIL_CODE/enroll/start", Map.of())
                .header("Authorization", "Bearer " + token));
        String code = server.getApplicationContext().getBean(CapturingEmailCodeChannel.class).lastCodeFor(email);
        exchange(HttpRequest.POST("/account/factors/EMAIL_CODE/enroll/confirm", Map.of("code", code))
                .header("Authorization", "Bearer " + token));
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
