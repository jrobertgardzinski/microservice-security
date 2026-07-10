package com.jrobertgardzinski;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies the offline-jwt library's committed pact — THE consumer of
 * {@code /.well-known/jwks.json}; the services delegate their offline verification to it — against
 * the real controller on the embedded server. Skipped, not failed, when the library repo is not
 * checked out next to this one.
 */
@Provider("microservice-security")
@PactFolder("../../offline-jwt/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "offline-jwt is not checked out next to this repo")
class JwksPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../offline-jwt/pacts"));
    }

    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
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
    void theKeySetShapeTheLibraryReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
