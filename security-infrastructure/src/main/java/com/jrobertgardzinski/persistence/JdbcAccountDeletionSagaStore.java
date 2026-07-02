package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Postgres-backed {@link AccountDeletionSagaStore} (see V6/V7). */
@Singleton
@Requires(beans = DataSource.class)
class JdbcAccountDeletionSagaStore implements AccountDeletionSagaStore {

    private final AccountDeletionSagaJdbcRepository repository;

    JdbcAccountDeletionSagaStore(AccountDeletionSagaJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public void start(UUID sagaId, String email, Instant at) {
        repository.save(new AccountDeletionSagaEntity(sagaId, email, "STARTED", false, false, at, at));
    }

    @Override
    public boolean confirm(String email, String participant, Instant at) {
        switch (participant) {
            case "memes" -> repository.confirmMemes(email, at);
            case "comments" -> repository.confirmComments(email, at);
            default -> throw new IllegalArgumentException("unknown saga participant: " + participant);
        }
        return repository.completeFullyConfirmed(email, at) > 0;
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
