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
    void a_missing_totp_key_refuses_a_prod_start() {
        Throwable refusal = catchThrowable(() -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties(Map.of(
                        "kafka.enabled", false,
                        "datasources.default.url", "jdbc:postgresql://localhost:1/none",
                        "datasources.default.username", "nobody",
                        "datasources.default.password", A_PASSWORD,
                        "security.jwt.private-key", "not-checked-here",
                        "security.jwt.public-key", "not-checked-here",
                        "security.mfa.recovery.pepper", "a-real-pepper",
                        "security.metrics.token", "a-scraper-token",
                        "flyway.datasources.default.enabled", false))
                .start());

        assertThat(refusal)
                .as("a TOTP seed MINTS codes for ever and silently; without a key of its own a"
                        + " stolen table is a copy of every authenticator app in it")
                .hasStackTraceContaining("the TOTP seeds need their own key");
    }

    @Test
    void an_open_metrics_endpoint_refuses_a_prod_start() {
        Throwable refusal = catchThrowable(() -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties(Map.of(
                        "kafka.enabled", false,
                        "datasources.default.url", "jdbc:postgresql://localhost:1/none",
                        "datasources.default.username", "nobody",
                        "datasources.default.password", A_PASSWORD,
                        "security.jwt.private-key", "not-checked-here",
                        "security.jwt.public-key", "not-checked-here",
                        "security.mfa.recovery.pepper", "a-real-pepper",
                        "security.mfa.secret-key", "ZGV2LWtleS1ub3QtYS1zZWNyZXQtMzJieXRlcyEhISE=",
                        "flyway.datasources.default.enabled", false))
                .start());

        assertThat(refusal)
                .as("there is no separate management port, so /prometheus is served on the API's own"
                        + " connector: without a token it answers whoever can reach the service")
                .hasStackTraceContaining("the metrics endpoint needs a token");
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
