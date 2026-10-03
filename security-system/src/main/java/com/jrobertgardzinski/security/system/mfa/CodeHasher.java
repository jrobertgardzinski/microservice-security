package com.jrobertgardzinski.security.system.mfa;

/**
 * Hashes a one-time code for storage against a challenge — the raw code is never kept. A port so
 * the hashing stays in the infrastructure layer (SHA-256, the same as the session-token hashing),
 * out of the domain and system layers.
 *
 * <p>It is a rule about STORED secrets, not about every use of a cipher: {@link TotpFactor}
 * computes its RFC 6238 HMAC right here on purpose, because that HMAC is the factor's definition
 * rather than a choice about how something is kept. {@code docs/mfa-design.md} places it that way —
 * "pure, self-contained, in system/mfa (no external I/O)" — so the two live side by side knowingly.
 */
@FunctionalInterface
public interface CodeHasher {

    String hash(String rawCode);
}
