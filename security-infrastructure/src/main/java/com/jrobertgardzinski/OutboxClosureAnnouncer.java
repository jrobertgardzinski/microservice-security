package com.jrobertgardzinski;

import com.jrobertgardzinski.closure.ClosureMessages;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.persistence.OutboxAppender;
import com.jrobertgardzinski.security.domain.account.ClosureAnnouncer;
import com.jrobertgardzinski.security.domain.account.AccountClosure;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The closure's announcements on the wire: the deletion fact on {@value #FACTS_TOPIC}, the mail on
 * {@value #MAIL_TOPIC} — both through the transactional outbox, so each commits with the saga
 * state that caused it. The JSON shapes are a pact with the portal and the mail service.
 */
@Singleton
final class OutboxClosureAnnouncer implements ClosureAnnouncer {

    static final String FACTS_TOPIC = "security-events";
    static final String MAIL_TOPIC = "mail-requests";

    private final OutboxAppender outbox;
    private final JsonMapper json;

    OutboxClosureAnnouncer(OutboxAppender outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public void announce(AccountClosure closure, UUID sagaId, UserId leaver) {
        Email email = closure.target();
        Map<String, Object> fact = new LinkedHashMap<>(Map.of(
                "id", UUID.randomUUID().toString(),
                "sagaId", sagaId.toString(),
                "type", ClosureMessages.ACCOUNT_DELETION_REQUESTED,
                "email", email.value(),
                // the legal basis of the closure, and the only thing that tells the content
                // services whether the policy beside it may be honoured at all. Additive within
                // envelope version 1 (workspace ADR 0004); an older consumer that ignores it
                // simply behaves as every consumer did before, which is why the field is stated
                // ALWAYS rather than only for the interesting value
                "initiatedBy", closure.requestedBy().wire(),
                "version", 1));
        // the leaver by identity, beside the address: what the portal keys its rows on since the
        // cutover. Additive within version 1; absent only for an account this service cannot find
        if (leaver != null) {
            fact.put(ClosureMessages.Field.USER_ID, leaver.toString());
        }
        if (!closure.choices().rules().isEmpty()) {
            fact.put("policy", closure.choices().rules());
        }
        outbox.append(FACTS_TOPIC, email.value(), write(fact));
    }

    @Override
    public void goodbye(Email leaver) {
        mail("ACCOUNT_DELETED", leaver.value());
    }

    @Override
    public void apology(Email leaver) {
        mail("ACCOUNT_DELETION_FAILED", leaver.value());
    }

    private void mail(String type, String to) {
        outbox.append(MAIL_TOPIC, to, write(Map.<String, Object>of(
                "id", UUID.randomUUID().toString(), "type", type, "to", to, "version", 1)));
    }

    private String write(Map<String, ?> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
