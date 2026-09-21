package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.persistence.InMemoryOutboxAppender;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChangeResult;
import io.micronaut.json.JsonMapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The move has to be ANNOUNCED, or the estate keeps the person's content under an address they no
 * longer own. Identity moves its own tables by hand and nothing told anyone else: the portal's
 * collections are keyed on the token's subject, so they read back empty after a confirmed change,
 * and a meme still filed under the old address is purged by a WHERE that matches nothing while the
 * purge reports success.
 *
 * <p>Driven through the real controller, because the fact's place is the point: it is appended
 * INSIDE the transaction that moved the account, so nobody can be told about a move that rolled
 * back and no move can commit unannounced. A pass-through boundary here — what the boundary does
 * is its own test; what this one watches is that the append happens within it, and only for a
 * confirmed move.
 */
@Epic("Identity")
@Feature("Change email")
class EmailChangeIsAnnouncedTest {

    private static final Email OLD = Email.of("alice@old.example.com");
    private static final Email NEW = Email.of("alice@new.example.com");
    private static final VerificationToken TOKEN = new VerificationToken("the-change-token");

    private final ConfirmEmailChange confirmEmailChange = mock(ConfirmEmailChange.class);
    private final InMemoryOutboxAppender outbox = new InMemoryOutboxAppender();

    @Test
    @DisplayName("a confirmed change announces EMAIL_CHANGED once, keyed by the new address")
    void a_confirmed_change_is_announced() throws Exception {
        when(confirmEmailChange.execute(TOKEN))
                .thenReturn(new ConfirmEmailChangeResult.EmailChanged(OLD, NEW));

        controller().confirm(Map.of("token", TOKEN.value()));

        assertEquals(1, outbox.appended().size(), "exactly one event belongs in the outbox");
        InMemoryOutboxAppender.AppendedEvent event = outbox.appended().get(0);
        assertEquals("security-events", event.topic());
        // the new address, so a later deletion request for the same person — keyed by the address
        // they have NOW — lands in the same partition, behind this rename
        assertEquals(NEW.value(), event.key(), "the fact is keyed by the NEW address");
        assertEquals(Map.of(
                        "id", "10d6b579-be97-3b2b-8c43-9eaaca6350dc",
                        "type", "EMAIL_CHANGED",
                        "oldEmail", OLD.value(),
                        // the new address, under the name every other fact on this topic uses
                        "email", NEW.value(),
                        "version", 1),
                JsonMapper.createDefault().readValue(event.payload(), Map.class));
    }

    @Test
    @DisplayName("a refused change announces nothing at all")
    void a_refused_change_is_not_announced() {
        List<ConfirmEmailChangeResult> refusals = List.of(
                new ConfirmEmailChangeResult.EmailTaken(),
                new ConfirmEmailChangeResult.InvalidToken());
        for (ConfirmEmailChangeResult refusal : refusals) {
            when(confirmEmailChange.execute(TOKEN)).thenReturn(refusal);

            controller().confirm(Map.of("token", TOKEN.value()));

            assertTrue(outbox.appended().isEmpty(),
                    refusal + " moved nothing, so there is nothing to announce — and a consumer"
                            + " acting on it would re-key rows onto an address nobody moved to");
        }
    }

    @Test
    @DisplayName("the id is derived from the pair, so a redelivery is byte-identical")
    void the_id_is_derived_not_random() {
        // the outbox is at-least-once: the same pair must always yield the same id, or a consumer
        // deduplicating on it re-keys the same move twice
        assertEquals(EmailChangedAnnouncer.idOf(OLD.value(), NEW.value()),
                EmailChangedAnnouncer.idOf(OLD.value(), NEW.value()));
        assertEquals(UUID.nameUUIDFromBytes(
                        (OLD.value() + "|" + NEW.value() + "|EMAIL_CHANGED")
                                .getBytes(StandardCharsets.UTF_8)),
                EmailChangedAnnouncer.idOf(OLD.value(), NEW.value()),
                "the derivation is the contract three other repositories are written against");
    }

    private ConfirmEmailChangeController controller() {
        return new ConfirmEmailChangeController(confirmEmailChange,
                new TransactionBoundary() {
                    @Override
                    public <T> T execute(Supplier<T> work) {
                        return work.get();
                    }
                },
                new EmailChangedAnnouncer(outbox, JsonMapper.createDefault()));
    }
}
