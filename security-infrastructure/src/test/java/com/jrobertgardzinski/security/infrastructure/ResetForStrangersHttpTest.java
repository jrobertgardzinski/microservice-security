package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.CapturingPasswordResetNotifier;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * "I forgot my password" is a public endpoint that takes any address a stranger types in, and it
 * used to mint a token and send a mail for every one of them.
 *
 * <p>Two things are being kept apart here. The ANSWER must not change — telling the caller whether
 * an address has an account is exactly the enumeration the quiet 202 exists to prevent. What must
 * change is what happens behind it: no token, no row, and above all no mail to somebody who never
 * asked this service for anything.
 */
@Epic("Password reset")
@Feature("Addresses with no account")
class ResetForStrangersHttpTest {

    private static final String PASSWORD = "StrongPassword1!";

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
    @DisplayName("an address with no account is answered the same way, and written to by nobody")
    void a_stranger_gets_the_same_answer_and_no_mail() {
        String stranger = "never-registered-here@example.com";

        HttpResponse<Map> answer = exchange(HttpRequest.POST("/reset-password/request",
                Map.of("email", stranger)));

        assertEquals(HttpStatus.ACCEPTED, answer.getStatus(),
                "the answer is the one every address gets — a different one here would tell a"
                        + " stranger which addresses are registered");
        assertNull(resetMails().lastTokenFor(stranger),
                "but nothing was mailed: a reset link sent to somebody with no account is a mail"
                        + " they never asked for, from a service they have never used");
    }

    @Test
    @DisplayName("a registered address still gets its link")
    void the_owner_still_gets_theirs() {
        String owner = "reset-owner@example.com";
        register(owner);

        exchange(HttpRequest.POST("/reset-password/request", Map.of("email", owner)));

        org.junit.jupiter.api.Assertions.assertNotNull(resetMails().lastTokenFor(owner),
                "the guard must refuse only the addresses nobody owns");
    }

    private void register(String email) {
        exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD)));
        String token = server.getApplicationContext()
                .getBean(com.jrobertgardzinski.CapturingEmailVerificationNotifier.class)
                .lastTokenFor(email);
        exchange(HttpRequest.POST("/verify-email", Map.of("token", token)));
    }

    private CapturingPasswordResetNotifier resetMails() {
        return server.getApplicationContext().getBean(CapturingPasswordResetNotifier.class);
    }

    @SuppressWarnings("rawtypes")
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException refused) {
            return (HttpResponse<Map>) refused.getResponse();
        }
    }
}
