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
 * The saga contract's OTHER direction: here the orchestrator is the CONSUMER. Each pact states
 * the exact shape of the USER_CONTENT_PURGED confirmation this service acts on — one pact per
 * participant, proven by driving the real listener with the pact's payload. The generated pacts
 * (../pacts, committed) are verified against the participants' REAL confirmation-building code by
 * their provider tests. Only the fields this listener reads are in the contract (type and email —
 * the participant is identified by the topic, not the payload); producers may add more.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class PurgeConfirmationsContractTest {

    private final AccountDeletionOrchestrator orchestrator = mock(AccountDeletionOrchestrator.class);
    private final PurgeConfirmationsListener listener = new PurgeConfirmationsListener(
            orchestrator,
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            },
            JsonMapper.createDefault());

    private static PactDslJsonBody confirmation() {
        return new PactDslJsonBody()
                .stringValue("type", "USER_CONTENT_PURGED")
                .stringType("email", "leaver@example.com");
    }

    @Pact(consumer = "microservice-security", provider = "microservice-memes")
    MessagePact memesConfirmation(MessagePactBuilder builder) {
        return builder.expectsToReceive("a user content purged confirmation")
                .withContent(confirmation())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "memesConfirmation")
    void recordsTheMemesConfirmation(List<Message> messages) {
        listener.fromMemes(messages.get(0).contentsAsString(), null);
        verify(orchestrator).completePurge("leaver@example.com", "memes");
    }

    @Pact(consumer = "microservice-security", provider = "microservice-comments")
    MessagePact commentsConfirmation(MessagePactBuilder builder) {
        return builder.expectsToReceive("a user content purged confirmation")
                .withContent(confirmation())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "commentsConfirmation")
    void recordsTheCommentsConfirmation(List<Message> messages) {
        listener.fromComments(messages.get(0).contentsAsString(), null);
        verify(orchestrator).completePurge("leaver@example.com", "comments");
    }

    @Pact(consumer = "microservice-security", provider = "microservice-user-collections")
    MessagePact collectionsConfirmation(MessagePactBuilder builder) {
        return builder.expectsToReceive("a user content purged confirmation")
                .withContent(confirmation())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "collectionsConfirmation")
    void recordsTheCollectionsConfirmation(List<Message> messages) {
        listener.fromCollections(messages.get(0).contentsAsString(), null);
        verify(orchestrator).completePurge("leaver@example.com", "collections");
    }
}
