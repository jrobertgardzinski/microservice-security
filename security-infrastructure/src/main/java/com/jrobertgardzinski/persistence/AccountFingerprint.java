package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.domain.vo.AttemptedAccount;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How an attempted account is written down in the failed-attempt log: as a keyed fingerprint, never
 * as the address.
 *
 * <p>The counter needs one thing only — whether two attempts were aimed at the SAME account — and
 * equality survives fingerprinting. The address itself does not need to survive, and writing it
 * down would build the thing nobody wants: a register of who tried to sign in as whom, including
 * addresses that belong to no account here at all. That register would be worth more to an attacker
 * who reached the database than the counter it serves.
 *
 * <p>A plain SHA-256 would not do. E-mail addresses are low-entropy and enumerable, so an unkeyed
 * digest is reversed with a dictionary in minutes. HMAC brings a server-side secret into the
 * computation, so the fingerprints cannot be recomputed without it.
 *
 * <p>Operationally: rotating {@code security.lockout.fingerprint-secret} invalidates every existing
 * fingerprint, which resets the counts. That is harmless here — the window this table serves is
 * fifteen minutes — and it is the reason this scheme suits THIS table and not, say, session storage.
 *
 * <p>The secret has a development default so the stack runs out of the box; a deployment that keeps
 * it is no worse off than one storing addresses in the clear, which is what this replaces.
 */
@Singleton
final class AccountFingerprint {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    AccountFingerprint(@Value("${security.lockout.fingerprint-secret:dev-only-lockout-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    String of(AttemptedAccount account) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(account.value().value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            // HmacSHA256 is required of every JRE, and any key length is valid for it
            throw new IllegalStateException("this JRE cannot compute " + ALGORITHM, impossible);
        }
    }
}
