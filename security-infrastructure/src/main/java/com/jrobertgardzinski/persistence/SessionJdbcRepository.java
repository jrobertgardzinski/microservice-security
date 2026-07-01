package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface SessionJdbcRepository extends CrudRepository<SessionEntity, String> {

    Optional<SessionEntity> findByAccessTokenHashAndStatus(String accessTokenHash, String status);

    @Query("UPDATE sessions SET status = :status WHERE refresh_token_hash = :refreshTokenHash")
    void updateStatus(String refreshTokenHash, String status);

    void deleteByFamilyId(UUID familyId);

    void deleteByEmail(String email);
}
