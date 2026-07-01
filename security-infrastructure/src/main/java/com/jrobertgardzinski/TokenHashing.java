package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.token.AbstractToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes session tokens (refresh and access) for storage and lookup. Stores index by this digest,
 * never the raw token, so a dump of a store does not hand out usable tokens. SHA-256 is the right
 * tool here (fast and deterministic — unlike a password hash, a token must be found by its digest),
 * and the tokens are 122-bit random UUIDs, so they are not guessable/dictionary-attackable.
 */
public final class TokenHashing {

    private TokenHashing() {
    }

    public static String hash(AbstractToken token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.value().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
