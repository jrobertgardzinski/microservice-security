package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface FederatedIdentityJdbcRepository extends CrudRepository<FederatedIdentityEntity, String> {

    void deleteByUserEmail(String userEmail);

    @Query("UPDATE federated_identities SET user_email = :toEmail WHERE user_email = :fromEmail")
    void repointUserEmail(String fromEmail, String toEmail);
}
