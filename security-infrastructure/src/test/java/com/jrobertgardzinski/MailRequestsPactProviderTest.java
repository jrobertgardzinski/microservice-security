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
 * Verifies microservice-email's committed pact against the REAL mail-request producers (see
 * {@link SecurityEventPacts}). File-based consumer-driven contracts: no broker — the consumer repo
 * checked out next to this one (as in the workspace and its CI) IS the source of the pact. When it
 * is not checked out (a truly standalone build), the verification is skipped, not failed.
 */
@Provider("microservice-security")
@PactFolder("../../microservice-email/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "microservice-email is not checked out next to this repo")
class MailRequestsPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../microservice-email/pacts"));
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of("com.jrobertgardzinski")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void everyMailRequestShapeTheConsumerReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
