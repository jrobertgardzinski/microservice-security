package com.jrobertgardzinski;

import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies microservice-user-collections' committed pact — the third saga participant's view of
 * the {@code content-commands} it purges on — against the REAL orchestrator (see
 * {@link SecurityEventPacts}). Skipped, not failed, when the consumer repo is not checked out
 * next to this one.
 */
@Provider("microservice-security")
@PactFolder("../../microservice-user-collections/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "microservice-user-collections is not checked out next to this repo")
class CollectionsPurgeCommandPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../microservice-user-collections/pacts"));
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of("com.jrobertgardzinski")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void everyCommandShapeTheParticipantReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
