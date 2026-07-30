package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.domain.vo.AttemptedAccount;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A law, in the spirit of ADR 0006: the account is fingerprinted in the ADAPTER and nowhere else.
 *
 * <p>The layers above speak in {@link AttemptedAccount} and know nothing about how it is stored.
 * That is the whole reason the pair could be introduced without an address ever being written into
 * the failed-attempt log: the counter needs equality, not the address, and the secret that turns
 * one into the other belongs to infrastructure.
 *
 * <p>The law names THIS mechanism and not cryptography in general, and the first draft got that
 * wrong: banning the words "hmac" and "digest" outright flagged {@code TotpFactor},
 * {@code WebauthnFactor} and {@code CodeHasher}, which talk about hashing because that is what they
 * legitimately are — a TOTP code IS an HMAC. A law that fires on honest neighbours teaches people
 * to switch it off. So what is forbidden below infrastructure is the vocabulary of the LOCKOUT
 * fingerprint: the {@link AccountFingerprint} type and the word that names its output.
 *
 * <p>It also pins the two properties the fingerprint must have, because a plain digest of an
 * e-mail address would satisfy neither: the same account must fingerprint the same way every time
 * (or counting breaks), and different accounts must not collide into one bucket (or one person's
 * failures would lock out another).
 */
class FingerprintingStaysInTheAdapterTest {

    private static final List<Path> LAYERS_BELOW_INFRASTRUCTURE = List.of(
            Path.of("../security-domain/src/main/java"),
            Path.of("../security-system/src/main/java"));

    @Test
    void no_layer_below_infrastructure_mentions_how_the_account_is_stored() throws IOException {
        for (Path layer : LAYERS_BELOW_INFRASTRUCTURE) {
            try (Stream<Path> sources = Files.walk(layer)) {
                List<String> leaks = sources
                        .filter(file -> file.toString().endsWith(".java"))
                        .filter(FingerprintingStaysInTheAdapterTest::mentionsFingerprinting)
                        .map(Path::toString)
                        .toList();
                assertTrue(leaks.isEmpty(),
                        "fingerprinting is the adapter's business — these files below infrastructure"
                                + " have learned about it, which means the boundary leaked: " + leaks);
            }
        }
    }

    private static boolean mentionsFingerprinting(Path file) {
        try {
            String source = Files.readString(file).toLowerCase();
            return source.contains("fingerprint");
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read " + file, unreadable);
        }
    }

    @Test
    void the_same_account_fingerprints_the_same_way_and_a_different_one_does_not() {
        AccountFingerprint fingerprint = new AccountFingerprint("a-test-secret");
        AttemptedAccount victim = AttemptedAccount.of(com.jrobertgardzinski.email.domain.Email.of("victim@example.com"));
        AttemptedAccount sameVictim = AttemptedAccount.of(com.jrobertgardzinski.email.domain.Email.of("victim@example.com"));
        AttemptedAccount somebodyElse = AttemptedAccount.of(com.jrobertgardzinski.email.domain.Email.of("other@example.com"));

        assertEquals(fingerprint.of(victim), fingerprint.of(sameVictim),
                "counting is equality: the same account must land in the same bucket every time");
        assertNotEquals(fingerprint.of(victim), fingerprint.of(somebodyElse),
                "two accounts sharing a bucket would let one person's failures lock out another");
    }

    @Test
    void the_address_itself_never_appears_in_what_is_stored() {
        AccountFingerprint fingerprint = new AccountFingerprint("a-test-secret");

        String stored = fingerprint.of(
                AttemptedAccount.of(com.jrobertgardzinski.email.domain.Email.of("victim@example.com")));

        assertTrue(stored.matches("[0-9a-f]{64}"), "expected a hex digest, got: " + stored);
        assertTrue(!stored.contains("victim") && !stored.contains("example"),
                "the log of failed attempts must not become a register of who tried to sign in as whom");
    }

    @Test
    void a_different_secret_produces_a_different_fingerprint() {
        AttemptedAccount account =
                AttemptedAccount.of(com.jrobertgardzinski.email.domain.Email.of("victim@example.com"));

        assertNotEquals(new AccountFingerprint("one-secret").of(account),
                new AccountFingerprint("another-secret").of(account),
                "without a secret in the computation, an address this guessable is a dictionary away"
                        + " from being recovered — which is why this is an HMAC and not a bare hash");
    }
}
