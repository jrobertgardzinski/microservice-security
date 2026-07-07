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
     * Severs every federated link of this account — the teardown when the account is deleted. A
     * stale link must not survive the account: if the freed address were later registered by
     * someone else, the old owner's provider identity would open the successor's account.
     */
    void unlinkAll(Email userEmail);

    /**
     * Re-points every federated link of this account to its new address. Called when the account's
     * email changes: the link is keyed by the provider's durable {@code subject} — the same person,
     * the same Google account — so it follows the account. (Severing instead would orphan the
     * identity: the provider keeps reporting its own OLD address, so the auto-link at the next
     * sign-in would never find the moved account — and could even find a stranger who registered
     * the freed address.)
     */
    void relinkAll(Email fromEmail, Email toEmail);
}
