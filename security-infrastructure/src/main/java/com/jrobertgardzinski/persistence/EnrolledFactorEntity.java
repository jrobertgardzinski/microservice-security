package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code enrolled_factors} table: one factor a user has registered. The natural key is
 * (user_email, type); {@code id} = "email|type" keeps micronaut-data's CRUD simple, the same trick
 * the block and federated-identity tables use. {@code secret_material} is factor-specific.
 */
@MappedEntity("enrolled_factors")
record EnrolledFactorEntity(@Id String id, String userEmail, String type, String label,
                            int factorOrder, String secretMaterial) {

    static String keyOf(String userEmail, String type) {
        return userEmail + "|" + type;
    }
}
