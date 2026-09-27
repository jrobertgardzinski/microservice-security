package com.jrobertgardzinski;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer's half of the retired key (workspace ADR 0008): <strong>this service tells nobody
 * when an address changes, because no other service is keyed by one.</strong> The consumers went
 * first — memes, comments and user-collections key every row by {@code UserId} and each guards it
 * with its own {@code RetiredAddressKeyTest} — and an announcement nobody listens for is worse than
 * none: it invites the next service to listen, and a listener is a key.
 *
 * <p>So the rule is stated where it can be broken: over this service's own main sources. A new
 * announcer, or the old fact re-emitted from somewhere else, fails the build here rather than being
 * discovered by a consumer that quietly starts re-keying rows again.
 *
 * <p><strong>The one legal use of the words.</strong> {@code ConfirmEmailChangeController} answers
 * the member's own confirmation request with {@code {"status": "EMAIL_CHANGED"}} — an HTTP reply to
 * the browser that asked, read by the UI, published to nobody. The test pins the set of files that
 * may say it, and separately pins that this file does not name the facts topic: a reply is not a
 * fact, and the difference is exactly the mistake the rule exists to catch.
 *
 * <p>Security's own stores stay keyed by the address on purpose — factors, recovery codes, federated
 * links, sessions — and their law is {@code AddressKeyedStoresTest}. This service OWNS the address;
 * what it may no longer do is make the rest of the estate depend on it.
 */
@Epic("Identity")
@Feature("The id is the only key")
@Story("No address-change announcement")
class AddressChangesAreAnnouncedToNobodyTest {

    /** The whole service from this module: every module's main sources, not just this one's. */
    private static final Path SERVICE = Path.of("..");

    /** The one file whose {@code EMAIL_CHANGED} is an HTTP reply, not a fact. */
    private static final String THE_HTTP_REPLY = "ConfirmEmailChangeController.java";

    /** The deleted announcer, and anything shaped like a replacement for it. */
    private static final Pattern ANNOUNCER = Pattern.compile("EmailChange[A-Za-z]*Announcer");

    /** Where facts are published; naming it beside EMAIL_CHANGED is what makes an announcement. */
    private static final String FACTS_TOPIC = "security-events";

    @Test
    @DisplayName("no main source announces an address change, and only the HTTP reply says the words")
    void the_words_live_in_one_reply_and_nowhere_else() throws IOException {
        Set<String> sayTheWords = new TreeSet<>();
        Set<String> announcers = new TreeSet<>();
        for (Path source : mainSources()) {
            String text = Files.readString(source);
            if (text.contains("EMAIL_CHANGED")) {
                sayTheWords.add(source.getFileName().toString());
            }
            if (ANNOUNCER.matcher(text).find()) {
                announcers.add(source.getFileName().toString());
            }
        }
        assertTrue(announcers.isEmpty(),
                "EmailChangedAnnouncer was deleted with the cutover: content elsewhere is keyed by "
                        + "UserId and has nothing to re-key when an address changes. These sources "
                        + "bring an announcer back: " + announcers);
        assertEquals(Set.of(THE_HTTP_REPLY), sayTheWords,
                "the words EMAIL_CHANGED belong to exactly one place — the HTTP reply "
                        + THE_HTTP_REPLY + " sends to the member who confirmed the change. Anywhere "
                        + "else they are a fact for somebody to consume, and no consumer of them "
                        + "exists any more");
    }

    @Test
    @DisplayName("the HTTP reply is a reply: it does not publish to the facts topic")
    void the_reply_is_not_a_fact() throws IOException {
        Path reply = mainSources().stream()
                .filter(source -> source.getFileName().toString().equals(THE_HTTP_REPLY))
                .findFirst()
                .orElseThrow(() -> new AssertionError(THE_HTTP_REPLY + " is gone: the rule above is "
                        + "passing for the wrong reason, and the UI's confirmation reply changed"));
        assertFalse(Files.readString(reply).contains(FACTS_TOPIC),
                THE_HTTP_REPLY + " names the facts topic beside EMAIL_CHANGED, which turns a reply "
                        + "to one browser into an announcement to the estate");
    }

    @Test
    @DisplayName("no pact promises an address-change fact to anybody")
    void no_consumer_is_promised_the_fact() throws IOException {
        Path pacts = Path.of("src/test/java/com/jrobertgardzinski/SecurityEventPacts.java");
        String declared = Files.readString(pacts);
        assertFalse(Pattern.compile("(?i)@PactVerifyProvider\\(\"[^\"]*e-?mail[^\"]*chang")
                        .matcher(declared).find(),
                "a provider pact still answers for an address-change fact, so some consumer still "
                        + "pins one: " + pacts);
        assertTrue(declared.contains("an account deletion requested fact"),
                "SecurityEventPacts no longer answers for the deletion fact either, so the rule "
                        + "above proves nothing: the pacts moved somewhere this test does not look");
    }

    @Test
    @DisplayName("the facts topic is still published: the rules above are about WHAT, not whether")
    void facts_are_still_published() throws IOException {
        List<Path> publishers = mainSources().stream()
                .filter(source -> readQuietly(source).contains(FACTS_TOPIC))
                .toList();
        assertFalse(publishers.isEmpty(),
                "nothing in this service names " + FACTS_TOPIC + " any more, so the rules above pass "
                        + "because publishing itself is gone — the deletion saga's fact included");
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> tree = Files.walk(SERVICE)) {
            return tree.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src/main/java"))
                    .toList();
        }
    }

    private static String readQuietly(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException e) {
            throw new AssertionError("unreadable source: " + source, e);
        }
    }
}
