package com.jrobertgardzinski.security.system.mfa;

import java.util.Optional;

/**
 * Holds sign-ins in flight, keyed by a one-shot ticket handed to the client after link #1. Short-
 * lived and in memory — a lost entry only means the user starts over from the password. The same
 * shape as the OAuth flow store.
 */
public interface PendingAuthenticationStore {

    /** Store a pending authentication and return the fresh ticket that addresses it. */
    String open(PendingAuthentication pending);

    Optional<PendingAuthentication> find(String ticket);

    void replace(String ticket, PendingAuthentication pending);

    void close(String ticket);
}
