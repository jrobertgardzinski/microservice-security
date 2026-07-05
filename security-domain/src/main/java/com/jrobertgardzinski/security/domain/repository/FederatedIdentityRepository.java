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
}
