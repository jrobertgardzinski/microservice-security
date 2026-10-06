package com.jrobertgardzinski.security.domain.core;

/**
 * The tokens a sign-in hands the caller, in their plain form — the only moment they exist outside
 * the session area, which keeps their hashes.
 */
public record IssuedSession(String plainAccessToken, String plainRefreshToken) {
}
