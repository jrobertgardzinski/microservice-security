package com.jrobertgardzinski.persistence;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "One running deletion saga per address" against a real PostgreSQL (poz. 26) — the invariant lives
 * in the schema (V22), so a fake would prove nothing about it.
 *
 * <p>Two halves, and both matter. The store must refuse to open a second saga; and the index must
 * refuse a second STARTED row even to a writer that never asks the store — otherwise the guarantee
 * is only as good as the last caller who remembered to check.
 */
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionSagaLatchTest {

    private static final String UNIQUE_VIOLATION = "23505";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static ApplicationContext context;

    @BeforeAll
    static void startContext() {
        context = ApplicationContext.run(Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.dialect", "POSTGRES",
                "flyway.datasources.default.enabled", true));
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("the Postgres store admits one running deletion per address")
    void one_running_saga_per_address() {
        AccountDeletionSagaStoreContract.oneRunningSagaPerAddress(
                context.getBean(AccountDeletionSagaStore.class),
                "jdbc-leaver@example.com", "jdbc-other@example.com");
    }

    @Test
    @DisplayName("the partial unique index refuses a second STARTED row even behind the store's back")
    void the_index_refuses_a_second_started_row() {
        String email = "jdbc-forked@example.com";
        Instant at = Instant.parse("2026-07-30T10:00:00Z");
        insertSaga(UUID.randomUUID(), email, "STARTED", at);

        assertThatThrownBy(() -> insertSaga(UUID.randomUUID(), email, "STARTED", at.plusSeconds(30)))
                .rootCause()
                .isInstanceOf(SQLException.class)
                .satisfies(sql -> assertThat(((SQLException) sql).getSQLState()).isEqualTo(UNIQUE_VIOLATION));

        // and the index constrains only the running ones: history piles up freely
        insertSaga(UUID.randomUUID(), email, "COMPENSATED", at.plusSeconds(60));
        insertSaga(UUID.randomUUID(), email, "COMPENSATED", at.plusSeconds(90));
    }

    /** Writes the row straight through the Micronaut Data repository — no store, no pre-check. */
    private static void insertSaga(UUID id, String email, String state, Instant at) {
        context.getBean(AccountDeletionSagaJdbcRepository.class)
                .save(new AccountDeletionSagaEntity(id, email, state, at, at));
    }
}
