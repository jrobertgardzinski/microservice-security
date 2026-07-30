package com.jrobertgardzinski.security.infrastructure;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Step-up runs behind a live session, but each start verifies a password and (for SECOND_FACTORS)
 * mails a code — so it must be rate-limited per source, or it is a full-speed password oracle and a
 * code mail-bomb (poz. 5). With a cap of one per window, the second step-up is refused with 429.
 */
@Epic("Authentication")
@Feature("Step-up throttle")
class StepUpThrottleHttpTest {

    private static final String PASSWORD = "StrongPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class,
                Map.of("security.step-up.max-per-window", 1), "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a second step-up from the same source is throttled with 429 + Retry-After")
    void second_step_up_is_throttled() {
        String email = "throttled@example.com";
        String token = onboard(email);

        // the first step-up is allowed (a wrong password still counts as one attempt)
        HttpResponse<Map> first = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", "WrongButStrong1!"))
                .header("Authorization", "Bearer " + token));
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, first.getStatus());

        // the second is refused before any password work or code is sent
        HttpResponse<Map> second = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", PASSWORD))
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, second.getStatus());
        assertNotNull(second.getHeaders().get("Retry-After"), "a throttled caller is told when to come back");
    }

    private String onboard(String email) {
        exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD)));
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken)));
        return (String) exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)))
                .getBody(Map.class).orElseThrow().get("accessToken");
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
