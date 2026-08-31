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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An address the domain cannot even read is a typo, not a fault of the server.
 *
 * Every endpoint that builds an {@code Email} straight from the request body used to let the value
 * object's exception escape: {@code "xyz"} came back as 500 with the domain's own sentence in the
 * body, and each attempt left a stack trace in the log. The answer belongs to the endpoint's own
 * vocabulary instead — the sign-in door answers like any other failed sign-in, and the two quiet
 * request doors stay quiet, because an answer reserved for malformed input is still a different
 * answer for SOME inputs.
 *
 * The addresses below are refused by different rules on purpose (no '@', a domain without a dot, a
 * dot at the edge, two dots in a row), so the test pins the BEHAVIOUR at the boundary rather than
 * one rule's spelling.
 */
@Epic("Registration")
@Feature("Malformed input at the HTTP boundary")
class MalformedEmailHttpTest {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        client.close();
        server.close();
    }

    @ParameterizedTest(name = "signing in with \"{0}\"")
    @ValueSource(strings = {"xyz", "user@wp", ".lead@wp.pl", "a..b@wp.pl", ""})
    @DisplayName("a sign-in with an unreadable address is refused like any other, not with a 500")
    void signInAnswersLikeAWrongPassword(String email) {
        HttpResponse<?> response = exchange(HttpRequest.POST("/authenticate",
                Map.of("email", email, "password", "StrongPassword1!")));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus(),
                "a malformed address must answer exactly like an unknown account");
    }

    @ParameterizedTest(name = "asking for a verification link for \"{0}\"")
    @ValueSource(strings = {"xyz", "user@wp", "a..b@wp.pl"})
    @DisplayName("a verification request stays quiet, malformed address or not")
    void verificationRequestStaysQuiet(String email) {
        assertSameAsForAValidAddress("/verify-email/request", email, "VERIFICATION_LINK_SENT");
    }

    @ParameterizedTest(name = "asking for a reset link for \"{0}\"")
    @ValueSource(strings = {"xyz", "user@wp", "a..b@wp.pl"})
    @DisplayName("a reset request stays quiet, malformed address or not")
    void resetRequestStaysQuiet(String email) {
        assertSameAsForAValidAddress("/reset-password/request", email, "RESET_LINK_SENT");
    }

    /** The malformed attempt must be indistinguishable from one for a well-formed stranger. */
    private void assertSameAsForAValidAddress(String path, String malformed, String expectedStatus) {
        HttpResponse<Map> stranger = exchange(HttpRequest.POST(path, Map.of("email", "nobody@example.com")));
        HttpResponse<Map> broken = exchange(HttpRequest.POST(path, Map.of("email", malformed)));

        assertEquals(HttpStatus.ACCEPTED, broken.getStatus());
        assertEquals(stranger.getStatus(), broken.getStatus());
        assertEquals(Map.of("status", expectedStatus), broken.getBody(Map.class).orElseThrow());
        assertEquals(stranger.getBody(Map.class).orElseThrow(), broken.getBody(Map.class).orElseThrow(),
                "an answer reserved for malformed input would be a way to probe the door");
    }

    @SuppressWarnings("unchecked")
    private <T> HttpResponse<T> exchange(HttpRequest<?> request) {
        try {
            return (HttpResponse<T>) client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<T>) e.getResponse();
        }
    }
}
