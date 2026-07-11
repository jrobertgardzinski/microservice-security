package com.jrobertgardzinski;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Identity as the CONSUMER of the portal's single outcome: these pacts state the exact shape of
 * the {@code offboarding-events} announcements this service acts on — proven by driving the real
 * listener with the pact's payload. The generated pact (../pacts, committed) is verified against
 * the portal orchestrator's REAL outcome-building code by its provider test. Only the fields this
 * listener reads are in the contract (type and email); the producer may add more.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "microservice-offboarding", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class OffboardingOutcomeContractTest {

    private final AccountDeletionOrchestrator orchestrator = mock(AccountDeletionOrchestrator.class);
    private final OffboardingOutcomeListener listener = new OffboardingOutcomeListener(
            orchestrator,
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            },
            JsonMapper.createDefault());

    @Pact(consumer = "microservice-security")
    MessagePact portalPurged(MessagePactBuilder builder) {
        return builder.expectsToReceive("a portal content purged announcement")
                .withContent(new PactDslJsonBody()
                        .stringValue("type", "PORTAL_CONTENT_PURGED")
                        .stringType("email", "leaver@example.com"))
                .toPact();
    }

    @Pact(consumer = "microservice-security")
    MessagePact portalPurgeFailed(MessagePactBuilder builder) {
        return builder.expectsToReceive("a portal purge failed announcement")
                .withContent(new PactDslJsonBody()
                        .stringValue("type", "PORTAL_PURGE_FAILED")
                        .stringType("email", "leaver@example.com"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "portalPurged")
    void thePurgedAnnouncementFinishesTheDeletion(List<Message> messages) {
        listener.handle(messages.get(0).contentsAsString());
        verify(orchestrator).completePurge("leaver@example.com");
    }

    @Test
    @PactTestFor(pactMethod = "portalPurgeFailed")
    void theFailureAnnouncementRollsTheDeletionBack(List<Message> messages) {
        listener.handle(messages.get(0).contentsAsString());
        verify(orchestrator).compensate("leaver@example.com");
    }
}
