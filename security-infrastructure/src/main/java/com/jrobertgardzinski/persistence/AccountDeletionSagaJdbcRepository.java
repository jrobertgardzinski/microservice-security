package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface AccountDeletionSagaJdbcRepository extends CrudRepository<AccountDeletionSagaEntity, UUID> {

    @Query("UPDATE account_deletion_sagas SET state = 'COMPLETED', updated_at = :at "
            + "WHERE email = :email AND state = 'STARTED'")
    long completeStarted(String email, Instant at);

    @Query("UPDATE account_deletion_sagas SET state = 'COMPENSATED', updated_at = :at "
            + "WHERE email = :email AND state = 'STARTED'")
    long compensateStarted(String email, Instant at);

    List<AccountDeletionSagaEntity> findByStateAndCreatedAtBefore(String state, Instant cutoff);

    @Query("UPDATE account_deletion_sagas SET state = 'COMPENSATED', updated_at = :at "
            + "WHERE id = :id AND state = 'STARTED'")
    long compensate(UUID id, Instant at);
}
