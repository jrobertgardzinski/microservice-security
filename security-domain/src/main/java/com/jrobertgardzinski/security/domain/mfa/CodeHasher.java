package com.jrobertgardzinski.security.domain.mfa;

/**
 * Hashes a one-time code for storage against a challenge — the raw code is never kept. The port is
 * declared here; its implementation lives in the infrastructure (SHA-256, the same as the
 * session-token hashing), so how a code is kept is not the use cases' business.
 *
 * <p>It is a rule about STORED secrets, not about every use of a cipher: {@code TotpFactor}, in
 * the system layer, computes its RFC 6238 HMAC itself on purpose, because that HMAC is the
 * factor's definition rather than a choice about how something is kept. {@code docs/mfa-design.md}
 * places it that way — "pure, self-contained, in system/mfa (no external I/O)".
 */
@FunctionalInterface
public interface CodeHasher {

    String hash(String rawCode);
}
