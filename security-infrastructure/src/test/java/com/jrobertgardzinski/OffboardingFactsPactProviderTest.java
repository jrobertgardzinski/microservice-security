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
 * Verifies microservice-offboarding's committed pact — the portal orchestrator's view of the
 * {@code ACCOUNT_DELETION_REQUESTED} fact it opens sagas on — against the REAL fact-producing
 * code (see {@link SecurityEventPacts}). Skipped, not failed, when the consumer repo is not
 * checked out next to this one.
 */
@Provider("microservice-security")
@PactFolder("../../microservice-offboarding/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "microservice-offboarding is not checked out next to this repo")
class OffboardingFactsPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../microservice-offboarding/pacts"));
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of("com.jrobertgardzinski")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void everyFactShapeThePortalReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
