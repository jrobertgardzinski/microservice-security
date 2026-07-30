package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Postgres-backed {@link AccountDeletionSagaStore} (see V6/V17). */
@Singleton
@Requires(beans = DataSource.class)
class JdbcAccountDeletionSagaStore implements AccountDeletionSagaStore {

    private final AccountDeletionSagaJdbcRepository repository;

    JdbcAccountDeletionSagaStore(AccountDeletionSagaJdbcRepository repository) {
        this.repository = repository;
    }

    /**
     * The read is the normal path; the partial unique index of V22 is the backstop for the race the
     * read cannot close. Losing that race raises a unique violation, which aborts the request's
     * whole transaction — the account lock and the deletion fact roll back with it, so the loser
     * changes nothing at all. That is the safe side of the trade: never a second STARTED row.
     */
    @Override
    public boolean start(UUID sagaId, String email, Instant at) {
        if (repository.existsByEmailAndState(email, "STARTED")) {
            return false;
        }
        repository.save(new AccountDeletionSagaEntity(sagaId, email, "STARTED", at, at));
        return true;
    }

    @Override
    public boolean complete(String email, Instant at) {
        return repository.completeStarted(email, at) > 0;
    }

    @Override
    public boolean compensate(String email, Instant at) {
        return repository.compensateStarted(email, at) > 0;
    }

    @Override
    public boolean lastSagaWasCompensated(String email) {
        return repository.findLatestByEmail(email)
                .map(saga -> "COMPENSATED".equals(saga.state()))
                .orElse(false);
    }

    @Override
    public List<String> compensateOverdue(Instant cutoff, Instant at) {
        List<String> emails = new ArrayList<>();
        for (AccountDeletionSagaEntity saga : repository.findByStateAndCreatedAtBefore("STARTED", cutoff)) {
            if (repository.compensate(saga.id(), at) > 0) {
                emails.add(saga.email());
            }
        }
        return emails;
    }
}
