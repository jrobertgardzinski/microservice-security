package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.persistence.OutboxAppender;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Announces a confirmed address change on the facts topic, because identity is not the only service
 * that keyed something on the address. The portal's memes, comments and collections are all stored
 * under the address the author had at the time and nothing cascades — so a move that nobody
 * announces silently strands them: the collections read back empty, and the freed address hands the
 * old content's authorship to whoever registers it next. This fact is the announcement; re-keying
 * the rows is each service's own job.
 *
 * <p>Through the outbox, like every other fact this service publishes: the append joins the
 * transaction that moved the account, so a committed move and its announcement stand or fall
 * together. A move announced without committing would have consumers re-key rows onto an address
 * nobody lives at; a move committed without an announcement is the defect this class closes.
 */
@Singleton
class EmailChangedAnnouncer {

    static final String TYPE = "EMAIL_CHANGED";

    private final OutboxAppender outbox;
    private final JsonMapper json;

    EmailChangedAnnouncer(OutboxAppender outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    void announce(Email oldEmail, Email newEmail) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", idOf(oldEmail.value(), newEmail.value()).toString());
        fact.put("type", TYPE);
        fact.put("oldEmail", oldEmail.value());
        // the NEW address under the name every other fact on this topic uses for the subject's
        // address, so a consumer reading the topic generically does not have to special-case this
        // one; oldEmail is the extra, and it is the one the consumers' rows are keyed by today
        fact.put("email", newEmail.value());
        // envelope version (workspace ADR 0004): fields only ever added within version 1
        fact.put("version", 1);
        // Keyed by the NEW address: a later fact about this person — a deletion request — is keyed
        // by the address they have now, so keying the rename by the new one puts the two in the
        // same partition and therefore in the right order.
        //
        // The honest limit: that only orders what travels on THIS topic. The portal's participants
        // receive their purge COMMANDS on content-commands, published by microservice-offboarding,
        // so a deletion requested seconds after a rename can still overtake the re-key — the
        // participant purges under the old address, finds nothing, and confirms an erasure that
        // erased nothing. The window is accepted rather than fixed: the durable answer is keying
        // the estate on a stable user id instead of an address, and PLAN-P18 deliberately deferred
        // that.
        outbox.append(AccountDeletionOrchestrator.FACTS_TOPIC, newEmail.value(), write(fact));
    }

    /**
     * The id is DERIVED from the pair, never random: the outbox is at-least-once, so a redelivery
     * has to be byte-identical for consumers to deduplicate on the id — the same reasoning
     * offboarding's {@code EventsRouter.outcome} gives for deriving its outcome ids.
     */
    static UUID idOf(String oldEmail, String newEmail) {
        return UUID.nameUUIDFromBytes(
                (oldEmail + "|" + newEmail + "|" + TYPE).getBytes(StandardCharsets.UTF_8));
    }

    private String write(Map<String, ?> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
