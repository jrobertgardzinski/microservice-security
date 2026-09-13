package com.jrobertgardzinski;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-GCM over the one piece of factor material that is a real secret: the TOTP seed.
 *
 * <p>{@code EnrolledFactor}'s javadoc, V11 and {@code docs/mfa-design.md} all promised "encrypted
 * at rest" and the column held the seed in the clear. That gap is worth more than it looks: a TOTP
 * seed is not a credential the holder can present once — it MINTS the codes, for ever, silently. A
 * copy of the table is a copy of every second factor in it, and nobody's phone would ever show a
 * sign of it.
 *
 * <p>The other factors' material is deliberately NOT encrypted: an e-mail address or a phone number
 * is where a code is sent (and the address is matched by queries when an account moves), and a
 * passkey's public key is public by construction. Encrypting those would cost the same and protect
 * nothing.
 *
 * <p><b>No data migration.</b> A value that does not carry the {@link #MARKER} is returned as it
 * is, so seeds written before this class keep working and quietly become ciphertext the next time
 * their row is written. The alternative — rewriting every row at deploy time — would need the key
 * to exist before the first boot that has one, which is the thing being introduced.
 *
 * <p>The key is deployment configuration ({@code security.mfa.secret-key}, base64, 16/24/32 bytes).
 * Under a declared {@code prod} profile it MUST be set, exactly as the recovery-code pepper must:
 * a key that ships in the repository is not a key. <b>Losing it costs every enrolled TOTP factor</b>
 * — those users re-enrol — which is why it belongs beside the database password in whatever holds
 * the deployment's secrets.
 */
@Context
@Singleton
public final class TotpSecretCipher {

    /** What tells an encrypted value from a seed written before this existed. */
    public static final String MARKER = "gcm:v1:";

    /** Dev and test only, and it says so out loud. */
    private static final String DEV_KEY = "ZGV2LWtleS1ub3QtYS1zZWNyZXQtMzJieXRlcyEhISE=";

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public TotpSecretCipher(@Value("${security.mfa.secret-key:}") String configured, Environment environment) {
        String inForce = configured == null || configured.isBlank() ? null : configured.strip();
        if (inForce == null && environment.getActiveNames().contains("prod")) {
            throw new IllegalStateException("outside dev/test the TOTP seeds need their own key -"
                    + " set security.mfa.secret-key (or SECURITY_MFA_SECRET_KEY) in the deployment,"
                    + " base64 of 16, 24 or 32 bytes; a key shipped in the repository is not a key,"
                    + " and without one a stolen table mints every user's codes for ever");
        }
        byte[] material = Base64.getDecoder().decode(inForce == null ? DEV_KEY : inForce);
        if (material.length != 16 && material.length != 24 && material.length != 32) {
            throw new IllegalStateException("security.mfa.secret-key must be base64 of 16, 24 or 32"
                    + " bytes (AES-128/192/256), got " + material.length);
        }
        this.key = new SecretKeySpec(material, "AES");
    }

    /** The stored form of a seed: a fresh IV every time, so two identical seeds do not look alike. */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] together = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, together, 0, iv.length);
            System.arraycopy(ciphertext, 0, together, iv.length, ciphertext.length);
            return MARKER + Base64.getEncoder().encodeToString(together);
        } catch (GeneralSecurityException failed) {
            throw new IllegalStateException("could not encrypt a TOTP seed", failed);
        }
    }

    /** A value without the marker is a seed from before this class — returned as it is. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(MARKER)) {
            return stored;
        }
        try {
            byte[] together = Base64.getDecoder().decode(stored.substring(MARKER.length()));
            byte[] iv = Arrays.copyOfRange(together, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(together, IV_BYTES, together.length - IV_BYTES),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failed) {
            // the key changed, or the row was tampered with. Either way this factor cannot be used
            // again and its owner must re-enrol; saying so beats a wrong code nobody can explain.
            throw new IllegalStateException("a stored TOTP seed could not be decrypted with the"
                    + " configured security.mfa.secret-key", failed);
        }
    }
}
