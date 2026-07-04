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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The registration throttle at the HTTP boundary: with a cap of one per window, a second register
 * from the same source is refused with 429 and a Retry-After — protecting the expensive (Argon2)
 * endpoint from CPU exhaustion and mass signups, before any work is done.
 */
@Epic("Registration")
@Feature("Per-source throttle")
class RegisterThrottleHttpTest {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class,
                Map.of("security.registration.max-per-window", 1), "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a second registration from the same source is throttled with 429 + Retry-After")
    void second_registration_is_throttled() {
        HttpResponse<Map> first = register("first@example.com");
        assertEquals(HttpStatus.CREATED, first.getStatus(), "the first registration goes through");

        HttpClientResponseException throttled = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class, () -> register("second@example.com"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, throttled.getStatus());
        assertNotNull(throttled.getResponse().getHeaders().get("Retry-After"),
                "a throttled caller is told when to come back");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> register(String email) {
        return client.exchange(
                HttpRequest.POST("/register", Map.of("email", email, "password", "StrongPassword1!")),
                Map.class);
    }
}
