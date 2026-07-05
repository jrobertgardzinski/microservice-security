package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import com.jrobertgardzinski.CapturingRegistrationNoticeNotifier;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-enumeration at the HTTP boundary: registering with a taken address answers exactly like a
 * fresh registration, so the wire never confirms an account exists. The truth travels by mail
 * instead — a fresh verification link while the address is still unverified (they probably lost
 * the first mail), a "you already have an account" notice once it is verified.
 */
@Epic("Registration")
@Feature("Anti-enumeration")
class RegisterEnumerationHttpTest {

    private EmbeddedServer server;
    private BlockingHttpClient client;
    private CapturingEmailVerificationNotifier verificationMails;
    private CapturingRegistrationNoticeNotifier noticeMails;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
        verificationMails = server.getApplicationContext().getBean(CapturingEmailVerificationNotifier.class);
        noticeMails = server.getApplicationContext().getBean(CapturingRegistrationNoticeNotifier.class);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a taken, verified address answers like a fresh registration and mails a notice instead")
    void verified_taken_address_is_indistinguishable_and_noticed_by_mail() {
        String email = "owner@example.com";
        HttpResponse<Map> fresh = register(email);
        assertEquals(HttpStatus.CREATED, fresh.getStatus());
        verify(email);
        String tokenBefore = verificationMails.lastTokenFor(email);

        HttpResponse<Map> retaken = register(email);

        assertEquals(fresh.getStatus(), retaken.getStatus());
        assertEquals(fresh.getBody(Map.class).orElseThrow(), retaken.getBody(Map.class).orElseThrow(),
                "the taken-email reply must not differ from the fresh one in any way");
        assertTrue(noticeMails.noticedEmails().contains(email),
                "the address owner is told about the attempt by mail");
        assertEquals(tokenBefore, verificationMails.lastTokenFor(email),
                "a verified address is not sent another verification link");
    }

    @Test
    @DisplayName("a taken, still-unverified address gets a fresh verification link, no notice")
    void unverified_taken_address_gets_a_fresh_link() {
        String email = "latecomer@example.com";
        assertEquals(HttpStatus.CREATED, register(email).getStatus());
        String firstToken = verificationMails.lastTokenFor(email);

        HttpResponse<Map> retaken = register(email);

        assertEquals(HttpStatus.CREATED, retaken.getStatus());
        assertNotEquals(firstToken, verificationMails.lastTokenFor(email),
                "re-registering before verifying re-sends the link with a fresh token");
        assertFalse(noticeMails.noticedEmails().contains(email),
                "no already-registered notice while the address is still unverified");
    }

    private void verify(String email) {
        HttpResponse<Map> verified = client.exchange(HttpRequest.POST("/verify-email",
                Map.of("token", verificationMails.lastTokenFor(email))), Map.class);
        assertEquals(HttpStatus.OK, verified.getStatus(), "failed to verify the seeded user");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> register(String email) {
        return client.exchange(
                HttpRequest.POST("/register", Map.of("email", email, "password", "StrongPassword1!")),
                Map.class);
    }
}
