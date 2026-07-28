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
 * checked out in the neighbouring portal workspace.
 *
 * <p>The path is ../../../portal/… and not ../../… because the estate split into three
 * workspaces on 2026-07-12 and this consumer moved to portal/. The old path pointed at
 * shared/, which simply does not exist — so @EnabledIf disabled this verification silently and
 * it reported GREEN (skipped, not failed) for weeks. A breaking change to the response shape
 * would have passed a full build without one red mark. {@code SilentlySkippedPactTest} now
 * fails if that ever happens again.
 */
@Provider("microservice-security")
@PactFolder("../../../portal/microservice-offboarding/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "microservice-offboarding is not checked out in the portal workspace")
class OffboardingFactsPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../../portal/microservice-offboarding/pacts"));
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
