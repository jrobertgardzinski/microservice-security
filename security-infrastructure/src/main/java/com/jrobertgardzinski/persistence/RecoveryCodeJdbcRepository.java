package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface RecoveryCodeJdbcRepository extends CrudRepository<RecoveryCodeEntity, String> {

    void deleteByUserEmail(String userEmail);

    long countByUserEmailAndUsedFalse(String userEmail);

    /** Atomic spend: flips exactly one unused row, so two racing proofs cannot share a code. */
    @Query("UPDATE recovery_codes SET used = TRUE WHERE id = :id AND used = FALSE")
    int spend(String id);
}
