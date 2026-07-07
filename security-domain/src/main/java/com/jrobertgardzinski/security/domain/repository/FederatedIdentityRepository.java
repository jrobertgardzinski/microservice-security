package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;

import java.util.Optional;

/**
 * The links between external identities and local accounts: one account, many identities — a
 * password, a Google subject, one day a Facebook subject are equal keys to the same user. Keyed
 * by {@code (provider, subject)}, the provider's durable opaque id; linking the same pair again
 * repoints it (upsert).
 */
public interface FederatedIdentityRepository {

    Optional<Email> findUserBy(String provider, String subject);

    void link(String provider, String subject, Email userEmail);

    /**
     * Severs every federated link of this account. Called when the account's email changes: the
     * provider vouched for the OLD address, so the link must not follow the account to the new
     * one — the user re-links naturally at their next federated sign-in (auto-link on a verified
     * account), and until then the identity opens nothing.
     */
    void unlinkAll(Email userEmail);
}
