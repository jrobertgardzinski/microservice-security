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
 * Verifies microservice-memes' committed pact — the gallery's view of {@code EMAIL_CHANGED},
 * the fact it re-keys its rows on — against the REAL announcing code (see
 * {@link SecurityEventPacts}). Skipped, not failed, when the consumer repo is not checked out in
 * the neighbouring portal workspace; {@code SilentlySkippedPactTest} is what stops that skip from
 * being silent.
 *
 * <p>Separate from {@link OffboardingFactsPactProviderTest} because a {@code @PactFolder} names one
 * directory and these two consumers keep their own. memes also owns an HTTP pact
 * ({@code pacts-http}, verified by {@link MeIntrospectionPactProviderTest}); this is its MESSAGE
 * pact, and the two live side by side for the same reason the two verifications do.
 */
@Provider("microservice-security")
@PactFolder("../../../portal/microservice-memes/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "microservice-memes is not checked out in the portal workspace")
class MemesFactsPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../../portal/microservice-memes/pacts"));
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of("com.jrobertgardzinski")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void everyFactShapeTheGalleryReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
