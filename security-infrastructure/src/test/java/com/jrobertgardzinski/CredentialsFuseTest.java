package com.jrobertgardzinski;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The fuses' refusal paths on a real {@code prod} boot (Flyway switched off so the refusal under
 * test is a fuse's, not a connection error's). The fuses themselves are absent under dev/test by
 * wiring — {@code @Requires(env = "prod")} — which every other test in this module proves by
 * booting.
 */
class CredentialsFuseTest {

    /** A real password and a real pair, so each case fails on the ONE thing it is about. */
    private static final String A_PASSWORD = "not-a-dev-default";

    @Test
    void a_missing_password_refuses_a_prod_start() {
        Throwable refusal = catchThrowable(() -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties(Map.of("kafka.enabled", false, "flyway.datasources.default.enabled", false))
                .start());

        assertThat(refusal).hasStackTraceContaining("requires an explicit datasource password");
    }

    @Test
    void a_known_dev_default_refuses_a_prod_start() {
        Throwable refusal = catchThrowable(() -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties(Map.of(
                        "kafka.enabled", false,
                        "datasources.default.password", "secret",
                        "flyway.datasources.default.enabled", false))
                .start());

        assertThat(refusal).hasStackTraceContaining("known dev default");
    }

    @Test
    void a_missing_jwt_signing_pair_refuses_a_prod_start() {
        Throwable refusal = catchThrowable(() -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties(Map.of(
                        "kafka.enabled", false,
                        // enough for the datasource BEAN to exist; nothing here opens a connection,
                        // and without them the prod file's ${DB_USER} placeholder fails first and
                        // the fuse under test never gets asked
                        "datasources.default.url", "jdbc:postgresql://localhost:1/none",
                        "datasources.default.username", "nobody",
                        "datasources.default.password", A_PASSWORD,
                        "flyway.datasources.default.enabled", false))
                .start());

        assertThat(refusal)
                .as("with no pair configured the service would generate one at startup and serve a"
                        + " JWK set that changes on every restart and differs between replicas —"
                        + " offline verifiers would fail intermittently with nothing to read")
                .hasStackTraceContaining("requires an explicit JWT signing pair");
    }
}
