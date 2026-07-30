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

    /**
     * The once-latch. It matches by address and state rather than by id, because the portal's
     * outcome names the address; the partial unique index of V22 is what makes that unambiguous —
     * exactly one STARTED row per address can exist, so this can never settle a saga other than
     * the one running (which is how it used to settle two at once).
     */
    @Query("UPDATE account_deletion_sagas SET state = 'COMPLETED', updated_at = :at "
            + "WHERE email = :email AND state = 'STARTED'")
    long completeStarted(String email, Instant at);

    @Query("UPDATE account_deletion_sagas SET state = 'COMPENSATED', updated_at = :at "
            + "WHERE email = :email AND state = 'STARTED'")
    long compensateStarted(String email, Instant at);

    /** Is a saga for this address in this state? Asked before opening one — see V22. */
    boolean existsByEmailAndState(String email, String state);

    List<AccountDeletionSagaEntity> findByStateAndCreatedAtBefore(String state, Instant cutoff);

    /** The most recent saga for this e-mail, whatever state it reached. */
    @Query("SELECT * FROM account_deletion_sagas WHERE email = :email"
            + " ORDER BY created_at DESC LIMIT 1")
    java.util.Optional<AccountDeletionSagaEntity> findLatestByEmail(String email);

    @Query("UPDATE account_deletion_sagas SET state = 'COMPENSATED', updated_at = :at "
            + "WHERE id = :id AND state = 'STARTED'")
    long compensate(UUID id, Instant at);

    /**
     * Retention: a saga that has REACHED a verdict is deleted once it is older than the cutoff.
     *
     * <p>Nothing ever deleted from this table either, and every row holds the subject's address in a
     * column of its own. So the one table whose whole purpose is to run "delete my account" was the
     * table that kept the address for good — a year after the deletion completed, the address was
     * still there to be read out of a dump.
     *
     * <p>{@code state <> 'STARTED'} is the guard: a saga still running is the one thing that must
     * never be swept out from under {@code compensateOverdue}, whatever its age. The age is measured
     * on {@code updated_at}, the column the verdict wrote.
     */
    @Query("DELETE FROM account_deletion_sagas WHERE state <> 'STARTED' AND updated_at < :cutoff")
    int deleteSettledBefore(Instant cutoff);
}
