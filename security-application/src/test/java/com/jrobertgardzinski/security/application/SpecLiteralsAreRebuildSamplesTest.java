package com.jrobertgardzinski.security.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A law, from {@code specs/README.md}: the literals in a {@code .feature} are samples of the
 * REBUILD level — what this service ships with — so the glue that judges them must use exactly
 * that policy and not one of its own.
 *
 * <p>The registration glue built {@code defaultsExcept(new MinLength(12), new SpecialChars("#?!"))}.
 * The scenarios still passed, which is the problem: "StrongPassword1!" was accepted and "weak"
 * refused for reasons no deployment holds, so the file documented a service that does not exist.
 * The README says a release changing {@code MinLength.DEFAULT} is SUPPOSED to turn these files red;
 * a glue with its own policy is precisely what stops that from happening.
 *
 * <p>Scoped to this layer on purpose. At the HTTP layer a scenario may SET a level and then assert
 * on it — that is a different thing from a glue quietly deciding the rules on its own, and
 * {@code password-policy.feature} is the feature that does it.
 */
class SpecLiteralsAreRebuildSamplesTest {

    private static final Path GLUE = Path.of("src/test/java/com/jrobertgardzinski/security/application/feature");

    /** Ways of saying "not the shipped policy" — each one makes the literals mean something else. */
    private static final List<String> OWN_POLICY = List.of(
            "defaultsExcept(", "new MinLength(", "new SpecialChars(",
            "new MinDigits(", "new MinUppercase(", "new MinLowercase(");

    @Test
    @DisplayName("no application-layer glue invents its own password policy")
    void the_glue_judges_by_what_the_service_ships_with() throws IOException {
        try (Stream<Path> sources = Files.walk(GLUE)) {
            List<String> offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(SpecLiteralsAreRebuildSamplesTest::buildsItsOwnPolicy)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

            assertThat(offenders)
                    .as("these judge the spec's literals by a policy of their own making, so the"
                            + " .feature describes a deployment nobody runs. Use"
                            + " PasswordPolicy::withDefaults — and if a scenario needs a different"
                            + " rule, let the scenario SET it, at the layer that can")
                    .isEmpty();
        }
    }

    private static boolean buildsItsOwnPolicy(Path source) {
        try {
            String text = Files.readString(source);
            return OWN_POLICY.stream().anyMatch(text::contains);
        } catch (IOException unreadable) {
            throw new IllegalStateException("could not read " + source, unreadable);
        }
    }
}
