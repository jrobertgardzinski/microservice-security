package com.jrobertgardzinski;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard's law, on the values MICRONAUT_ENVIRONMENTS can actually carry. The guard runs at
 * the top of {@code main} — before the context exists — because an undeclared context dies on a
 * confusing injection error long before any bean could refuse it by name.
 */
class ProfileGuardTest {

    @Test
    void an_undeclared_start_is_refused_by_name() {
        assertThatThrownBy(() -> ProfileGuard.requireDeclaredProfile(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no deployment profile declared");
        assertThatThrownBy(() -> ProfileGuard.requireDeclaredProfile(null))
                .hasMessageContaining("no deployment profile declared");
    }

    @Test
    void an_ambiguous_start_is_refused() {
        assertThatThrownBy(() -> ProfileGuard.requireDeclaredProfile("dev,prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both 'dev' and 'prod'");
    }

    @Test
    void a_declared_profile_passes_with_its_allies() {
        assertThatCode(() -> ProfileGuard.requireDeclaredProfile("dev")).doesNotThrowAnyException();
        assertThatCode(() -> ProfileGuard.requireDeclaredProfile("prod,k8s")).doesNotThrowAnyException();
    }

    @Test
    void an_environment_outside_the_trio_alone_is_not_a_declaration() {
        assertThatThrownBy(() -> ProfileGuard.requireDeclaredProfile("k8s"))
                .hasMessageContaining("no deployment profile declared");
    }
}
