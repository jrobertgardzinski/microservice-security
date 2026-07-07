package com.jrobertgardzinski.security.system.mfa;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * What the boundary remembers between issuing a challenge and verifying the proof: the hashed
 * secret (never the raw code) and when it expires. Single-use and attempt-limited are the chain's
 * concern (they live on the pending authentication), not the challenge's.
 *
 * <p>{@code publicData} is the ONE thing a factor may need to hand the client to answer the
 * challenge — null for code factors (the code is a secret mailed out of band), a base64url nonce
 * for WebAuthn (a public value the authenticator signs). It is surfaced in the MFA 202 responses
 * and never used for verification (verification is against {@code codeHash}).
 */
public record Challenge(String codeHash, LocalDateTime expiresAt, String publicData) {

    /** A code-factor challenge: nothing public to send, the code went out by mail/SMS. */
    public static Challenge secret(String codeHash, LocalDateTime expiresAt) {
        return new Challenge(codeHash, expiresAt, null);
    }

    /** A challenge whose {@code publicData} the client needs to answer it (e.g. a WebAuthn nonce). */
    public static Challenge withPublicData(String codeHash, LocalDateTime expiresAt, String publicData) {
        return new Challenge(codeHash, expiresAt, publicData);
    }

    public boolean isExpired(Clock clock) {
        return expiresAt.isBefore(LocalDateTime.now(clock));
    }
}
