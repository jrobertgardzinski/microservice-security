package com.jrobertgardzinski.security.infrastructure;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The per-source throttles on the other expensive anonymous endpoints: reset-password and
 * verify-email requests mint tokens and send mails, so each gets its own {@code SourceThrottle}
 * window — 429 + Retry-After past the cap, and a burst against one endpoint does not starve the
 * other (separate instances).
 */
@Epic("Throttling")
@Feature("Per-source throttle")
class RequestThrottleHttpTest {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private void start(Map<String, Object> properties) {
        server = ApplicationContext.run(EmbeddedServer.class, properties, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a second reset-password request from the same source is throttled with 429 + Retry-After")
    void reset_password_request_is_throttled() {
        start(Map.of("security.password-reset.max-per-window", 1));
        HttpResponse<Map> first = post("/reset-password/request", Map.of("email", "someone@example.com"));
        assertEquals(HttpStatus.ACCEPTED, first.getStatus(), "the first request goes through");

        HttpClientResponseException throttled = assertThrows(HttpClientResponseException.class,
                () -> post("/reset-password/request", Map.of("email", "someone.else@example.com")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, throttled.getStatus());
        assertNotNull(throttled.getResponse().getHeaders().get("Retry-After"),
                "a throttled caller is told when to come back");
    }

    @Test
    @DisplayName("a second verify-email request from the same source is throttled with 429 + Retry-After")
    void verify_email_request_is_throttled() {
        start(Map.of("security.verification.max-per-window", 1));
        HttpResponse<Map> first = post("/verify-email/request", Map.of("email", "someone@example.com"));
        assertEquals(HttpStatus.ACCEPTED, first.getStatus(), "the first request goes through");

        HttpClientResponseException throttled = assertThrows(HttpClientResponseException.class,
                () -> post("/verify-email/request", Map.of("email", "someone.else@example.com")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, throttled.getStatus());
        assertNotNull(throttled.getResponse().getHeaders().get("Retry-After"),
                "a throttled caller is told when to come back");
    }

    @Test
    @DisplayName("the throttles are separate: exhausting one endpoint leaves the other open")
    void throttles_are_separate_per_endpoint() {
        start(Map.of("security.password-reset.max-per-window", 1,
                "security.verification.max-per-window", 1));
        assertEquals(HttpStatus.ACCEPTED,
                post("/reset-password/request", Map.of("email", "someone@example.com")).getStatus());
        assertThrows(HttpClientResponseException.class,
                () -> post("/reset-password/request", Map.of("email", "someone@example.com")));

        assertEquals(HttpStatus.ACCEPTED,
                post("/verify-email/request", Map.of("email", "someone@example.com")).getStatus(),
                "the verify-email window is untouched by the reset-password burst");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> post(String path, Map<String, String> body) {
        return client.exchange(HttpRequest.POST(path, body), Map.class);
    }
}
