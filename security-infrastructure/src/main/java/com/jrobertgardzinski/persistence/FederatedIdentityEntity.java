package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

/**
 * Row of the {@code federated_identities} table: which local account a provider's subject opens.
 * The natural key is {@code (provider, subject)}; a single string id keeps micronaut-data's CRUD
 * simple, same trick as the block table's IP key.
 */
@MappedEntity("federated_identities")
record FederatedIdentityEntity(@Id String providerSubject, String provider, String subject,
                               String userEmail, LocalDateTime linkedAt) {

    static String keyOf(String provider, String subject) {
        return provider + "|" + subject;
    }
}
