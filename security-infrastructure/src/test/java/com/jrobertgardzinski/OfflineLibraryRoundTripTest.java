package com.jrobertgardzinski;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.offlinejwt.OfflineJwtVerifier;
import com.jrobertgardzinski.offlinejwt.VerifiedToken;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The closing loop of the offline-verification contract: a REAL access token this service mints,
 * verified through the REAL offline-jwt library — the same code every offline consumer
 * (memes, comments, paddock, user-collections, formula) runs — against the REAL JWKS endpoint.
 * The JWKS pact pins the key-set shape; this test pins the token itself: algorithm, kid, issuer,
 * expiry and subject can no longer drift away from the verifiers.
 */
@DisplayName("A token security mints verifies through the library its consumers use")
class OfflineLibraryRoundTripTest {

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
    void a_real_token_verifies_through_the_shared_library() {
        String email = "round-trip@example.com";
        String token = registerVerifyAuthenticate(email);

        OfflineJwtVerifier theConsumersVerifier =
                OfflineJwtVerifier.overHttp(server.getURL().toString(), new ObjectMapper());
        Optional<VerifiedToken> verified = theConsumersVerifier.verify(token);

        assertTrue(verified.isPresent(), "the library accepts what the service mints");
        assertEquals(email, verified.get().subject());
        assertTrue(verified.get().roles().contains("USER"), "roles ride the token into the library");
    }

    private String registerVerifyAuthenticate(String email) {
        client.exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD)), Map.class);
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        client.exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken)), Map.class);
        return (String) client.exchange(
                        HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)), Map.class)
                .getBody(Map.class).orElseThrow().get("accessToken");
    }
}
