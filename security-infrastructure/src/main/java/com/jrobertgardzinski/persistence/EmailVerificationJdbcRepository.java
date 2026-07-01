package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface EmailVerificationJdbcRepository extends CrudRepository<EmailVerificationEntity, String> {

    Optional<EmailVerificationEntity> findByPendingTokenHash(String pendingTokenHash);

    @Query("UPDATE email_verifications SET verified = true, pending_token_hash = null "
            + "WHERE pending_token_hash = :hash")
    void markVerified(String hash);
}
