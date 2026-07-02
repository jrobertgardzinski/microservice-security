package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link AccountDeletionSagaStore} for the database-less test environment. */
@Singleton
@Requires(missingBeans = DataSource.class)
class InMemoryAccountDeletionSagaStore implements AccountDeletionSagaStore {

    private record Saga(UUID id, String email, String state, Instant createdAt) {}

    private final Map<UUID, Saga> sagas = new ConcurrentHashMap<>();

    @Override
    public void start(UUID sagaId, String email, Instant at) {
        sagas.put(sagaId, new Saga(sagaId, email, "STARTED", at));
    }

    @Override
    public synchronized boolean complete(String email, Instant at) {
        for (Saga saga : sagas.values()) {
            if (saga.email().equals(email) && saga.state().equals("STARTED")) {
                sagas.put(saga.id(), new Saga(saga.id(), saga.email(), "COMPLETED", saga.createdAt()));
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized List<String> compensateOverdue(Instant cutoff, Instant at) {
        List<String> emails = new ArrayList<>();
        for (Saga saga : sagas.values()) {
            if (saga.state().equals("STARTED") && saga.createdAt().isBefore(cutoff)) {
                sagas.put(saga.id(), new Saga(saga.id(), saga.email(), "COMPENSATED", saga.createdAt()));
                emails.add(saga.email());
            }
        }
        return emails;
    }
}
