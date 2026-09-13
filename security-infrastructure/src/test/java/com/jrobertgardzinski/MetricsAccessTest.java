package com.jrobertgardzinski;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Metrics ride on the API's own connector — Micronaut has no separate management port — so
 * {@code /prometheus} was answering anybody who could reach the service: per-route request timings,
 * pool saturation, JVM vitals, and a live view of whether an attack is working.
 *
 * <p>Two claims here, and the second matters as much as the first: the endpoint closes when a token
 * is configured, and {@code /health} stays open whatever happens. A probe that needs credentials
 * fails during exactly the incidents it exists for.
 */
@Epic("Operations")
@Feature("Metrics access")
class MetricsAccessTest {

    private static final String TOKEN = "a-scraper-token";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("with a token configured, a scrape without it is refused and with it is served")
    void the_endpoint_closes_when_a_token_is_set() {
        start(Map.of("security.metrics.token", TOKEN));

        assertThat(statusOf(HttpRequest.GET("/prometheus")))
                .as("an open metrics endpoint on the API's port is a live view of this service for"
                        + " whoever can reach it")
                .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        assertThat(statusOf(HttpRequest.GET("/prometheus").header("Authorization", "Bearer wrong")))
                .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        assertThat(statusOf(HttpRequest.GET("/prometheus").header("Authorization", "Bearer " + TOKEN)))
                .as("and the scraper that has the token still gets its metrics")
                .isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    @DisplayName("health never needs a credential, token or no token")
    void a_probe_is_not_asked_to_authenticate() {
        start(Map.of("security.metrics.token", TOKEN));

        assertThat(statusOf(HttpRequest.GET("/health")))
                .as("a readiness probe that needs credentials fails during the incidents it exists"
                        + " for — and this answers {\"status\":\"UP\"} and nothing else")
                .isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    @DisplayName("with no token configured the endpoint is open, as the dev stack expects")
    void the_dev_stack_needs_nothing_configured() {
        start(Map.of());

        assertThat(statusOf(HttpRequest.GET("/prometheus")))
                .as("the compose stack's Prometheus scrapes over a private network; making it"
                        + " configure a token to work would be a change nobody asked for")
                .isEqualTo(HttpStatus.OK.getCode());
    }

    private void start(Map<String, Object> properties) {
        server = ApplicationContext.run(EmbeddedServer.class, properties, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    /** The CODE, not the enum: HttpStatus is a CharSequence, and assertThat cannot tell which one you mean. */
    private int statusOf(HttpRequest<?> request) {
        Throwable refused = catchThrowable(() -> client.exchange(request, String.class));
        if (refused == null) {
            return HttpStatus.OK.getCode();
        }
        if (refused instanceof HttpClientResponseException response) {
            return response.getStatus().getCode();
        }
        throw new IllegalStateException(refused);
    }
}
