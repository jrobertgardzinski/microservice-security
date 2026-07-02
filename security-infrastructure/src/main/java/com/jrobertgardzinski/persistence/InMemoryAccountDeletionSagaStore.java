package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link AccountDeletionSagaStore} for the database-less test environment. */
@Singleton
@Requires(missingBeans = DataSource.class)
class InMemoryAccountDeletionSagaStore implements AccountDeletionSagaStore {

    private record Saga(UUID id, String email, String state, Set<String> confirmed, Instant createdAt) {}

    private final Map<UUID, Saga> sagas = new ConcurrentHashMap<>();

    @Override
    public void start(UUID sagaId, String email, Instant at) {
        sagas.put(sagaId, new Saga(sagaId, email, "STARTED", new HashSet<>(), at));
    }

    @Override
    public synchronized boolean confirm(String email, String participant, Instant at) {
        for (Saga saga : sagas.values()) {
            if (saga.email().equals(email) && saga.state().equals("STARTED")) {
                saga.confirmed().add(participant);
                if (saga.confirmed().containsAll(Set.of("memes", "comments"))) {
                    sagas.put(saga.id(), new Saga(saga.id(), saga.email(), "COMPLETED",
                            saga.confirmed(), saga.createdAt()));
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public synchronized List<String> compensateOverdue(Instant cutoff, Instant at) {
        List<String> emails = new ArrayList<>();
        for (Saga saga : sagas.values()) {
            if (saga.state().equals("STARTED") && saga.createdAt().isBefore(cutoff)) {
                sagas.put(saga.id(), new Saga(saga.id(), saga.email(), "COMPENSATED",
                        saga.confirmed(), saga.createdAt()));
                emails.add(saga.email());
            }
        }
        return emails;
    }
}
