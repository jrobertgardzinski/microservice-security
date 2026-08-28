package com.jrobertgardzinski;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The fuse's refusal paths on a real {@code prod} boot (Flyway switched off so the refusal under
 * test is the fuse's, not a connection error's). The fuse itself is absent under dev/test by
 * wiring — {@code @Requires(notEnv)} — which every other test in this module proves by booting.
 */
class CredentialsFuseTest {

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
}
