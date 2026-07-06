package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code recovery_codes} table: one (hashed) recovery code, spent or not. The natural
 * key is (user_email, code_hash); {@code id} = "email|hash" keeps micronaut-data's CRUD simple,
 * the same trick the enrolled-factor table uses.
 */
@MappedEntity("recovery_codes")
record RecoveryCodeEntity(@Id String id, String userEmail, String codeHash, boolean used) {

    static String keyOf(String userEmail, String codeHash) {
        return userEmail + "|" + codeHash;
    }
}
