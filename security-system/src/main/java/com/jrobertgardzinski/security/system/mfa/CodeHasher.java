package com.jrobertgardzinski.security.system.mfa;

/**
 * Hashes a one-time code for storage against a challenge — the raw code is never kept. A port so
 * the crypto stays in the infrastructure layer (SHA-256, the same as the session-token hashing),
 * out of the domain and system layers.
 */
@FunctionalInterface
public interface CodeHasher {

    String hash(String rawCode);
}
