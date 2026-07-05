package com.jrobertgardzinski;

import com.jrobertgardzinski.security.system.mfa.CodeHasher;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 {@link CodeHasher} — the same treatment session tokens get: only the digest of a one-time
 * code is remembered, never the code. A short numeric code is not as unguessable as a token, but
 * the challenge is short-lived, attempt-limited and thrown away on use, so an offline attack on a
 * leaked digest has nothing to chew on for long.
 */
@Singleton
final class Sha256CodeHasher implements CodeHasher {

    @Override
    public String hash(String rawCode) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
