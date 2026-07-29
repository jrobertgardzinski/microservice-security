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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The preflight answer a browser needs before it will send a sign-in — the one header whose absence
 * is completely silent on this side of the wire.
 *
 * <p><strong>Why this test exists.</strong> Every UI in the estate lives on a different origin from
 * this service (the gallery on 8083, collections on 8093, security's own on 4200) and signs in with
 * {@code credentials: 'include'}, because the refresh token is an HttpOnly cookie. A browser refuses
 * such a response unless the preflight carries {@code Access-Control-Allow-Credentials: true} — and
 * refuses it CLIENT-SIDE: the request is never sent, so this service logs nothing, returns nothing,
 * and looks perfectly healthy while nobody in the portal can sign in.
 *
 * <p>That is not hypothetical. Micronaut 5 flipped the default for that flag from true to false, and
 * the configuration had been relying on the default. Seventeen of eighteen browser scenarios failed
 * in CI; 788 KB of collected container logs contained the word "CORS" exactly zero times, because
 * the server genuinely never saw the requests. It was found only by reading the browser's console.
 *
 * <p>So the check belongs here, three seconds from the configuration it guards, rather than only in
 * an end-to-end suite that needs the whole compose stack and a real Chromium to say the same thing.
 * An allowed origin and a permitted method are not enough on their own; the credentials flag is what
 * the browser actually gates on.
 */
@Epic("Identity")
@Feature("CORS")
class CorsPreflightTest {

    /** One of the shipped origins — the meme gallery, which is what the browser e2e drives. */
    private static final String GALLERY = "http://localhost:8083";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private HttpResponse<?> preflight(String origin) {
        return preflight(origin, Map.of());
    }

    private HttpResponse<?> preflight(String origin, Map<String, Object> properties) {
        server = ApplicationContext.run(EmbeddedServer.class, properties, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
        return client.exchange(HttpRequest.OPTIONS("/authenticate")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"));
    }

    @Test
    @DisplayName("the preflight allows credentials, without which no browser sends the sign-in at all")
    void the_preflight_allows_credentials() {
        HttpResponse<?> response = preflight(GALLERY);

        assertEquals(GALLERY, response.header("Access-Control-Allow-Origin"),
                "the gallery's origin has to be recognised before anything else matters");
        assertEquals("true", response.header("Access-Control-Allow-Credentials"),
                "the sign-in travels with credentials: 'include' for the refresh cookie; without this"
                        + " header the browser discards the response and the service never learns of it");
    }

    @Test
    @DisplayName("a deployment can replace the whole origin list — this is what k3s and hosting rely on")
    void the_origin_list_is_replaceable_by_configuration() {
        // the shipped defaults are compose's localhost ports; a cluster serves the same UIs from
        // ingress host names, and CORS is judged on the ORIGIN the browser reports, not on where the
        // request lands. Without this being configurable the service only works on a laptop.
        String ingress = "http://memes.portal.localhost:9080";
        HttpResponse<?> response = preflight(ingress,
                Map.of("micronaut.server.cors.configurations.ui.allowed-origins", ingress));

        assertEquals(ingress, response.header("Access-Control-Allow-Origin"));
        assertEquals("true", response.header("Access-Control-Allow-Credentials"),
                "replacing the origins must not quietly drop the credentials allowance with them");
    }

    @Test
    @DisplayName("and an origin nobody configured is refused outright, not merely left unallowed")
    void an_unknown_origin_is_refused() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> preflight("http://evil.example.com"),
                "allow-credentials is only safe while the origin list is genuinely a list — this is"
                        + " the half that keeps the assertion above from being a hole");

        // stronger than the "no Allow-Origin header" this test first asserted: Micronaut turns an
        // unconfigured origin away with a status rather than answering and omitting the header
        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }
}
