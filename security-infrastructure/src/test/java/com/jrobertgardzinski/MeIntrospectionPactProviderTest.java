package com.jrobertgardzinski;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Verifies microservice-memes' committed introspection pact — the fields its production gate
 * reads off {@code GET /me}, and the 401 for a token this service does not recognise — against
 * the real controller on the embedded server. The "signed-in user" provider state walks the real
 * flow (register, verify, authenticate); the request filter then swaps the pact's placeholder
 * bearer for the real access token it earned. Skipped, not failed, when the consumer repo is not
 * checked out next to this one.
 */
@Provider("microservice-security")
@PactFolder("../../microservice-memes/pacts-http")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "microservice-memes is not checked out next to this repo")
class MeIntrospectionPactProviderTest {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String PLACEHOLDER = "Bearer valid-session-token";

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../microservice-memes/pacts-http"));
    }

    private static EmbeddedServer server;
    private static BlockingHttpClient client;

    private String accessToken;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", server.getPort()));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void everyIntrospectionShapeTheConsumerReliesOn(PactVerificationContext context,
                                                    org.apache.hc.core5.http.HttpRequest request) {
        // the pact's placeholder bearer becomes the session the state just opened; 401 cases pass through
        org.apache.hc.core5.http.Header authorization = request.getFirstHeader("Authorization");
        if (authorization != null && PLACEHOLDER.equals(authorization.getValue())) {
            request.setHeader("Authorization", "Bearer " + accessToken);
        }
        context.verifyInteraction();
    }

    @State("a session for a signed-in user exists")
    void aSignedInUser() {
        String email = "introspected-" + System.nanoTime() + "@example.com";
        client.exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD)), Map.class);
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        client.exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken)), Map.class);
        accessToken = (String) client.exchange(
                        HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)), Map.class)
                .getBody(Map.class).orElseThrow().get("accessToken");
    }

}
