package com.jrobertgardzinski.security.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A law about the middle layer: every {@code .feature} is driven through the real HTTP stack by a
 * {@code RunHttp*Test}, or it is on the list below with the reason it is not.
 *
 * <p>{@code specs/README.md} said "one RunHttp*Test per file" and there were sixteen suites for
 * nineteen files. Nothing said which three were missing, so the sentence read as a guarantee while
 * being an aspiration — and the gap it hid is the interesting kind: a feature that runs only at the
 * use-case layer is a feature whose HTTP contract (status codes, cookies, the shapes a client
 * actually sees) nobody drives from the spec.
 *
 * <p>The exemptions are named, dated by the review that found them, and each says what DOES cover
 * the behaviour in the meantime. Adding a feature without a suite is now a decision somebody makes
 * out loud.
 */
class EverySpecHasAnHttpSuiteTest {

    private static final Path SPECS = Path.of("../specs");
    private static final Path SUITES = Path.of("src/test/java/com/jrobertgardzinski/security/infrastructure");

    /**
     * Features with no HTTP suite yet (TEST-2, review of 2026-09-08), and what covers them instead.
     * Each line is a debt, not a design: the behaviour is tested, the SPEC is not driven here.
     */
    private static final Map<String, String> NO_SUITE_YET = Map.of(
            "federated-sign-in.feature", "OauthFlowHttpTest drives the provider dance end to end,"
                    + " but as a hand-written test rather than from this spec",
            "mfa.feature", "MfaHttpTest covers the chain, the recovery code and the step-up guard"
                    + " over real HTTP; the spec itself runs only at the use-case layer",
            "mfa-passkey.feature", "MfaHttpTest signs a real ES256 assertion over the wire; the"
                    + " browser layer drives this spec, the HTTP layer does not");

    @Test
    @DisplayName("every spec is driven over HTTP, or is listed as not yet")
    void no_spec_is_quietly_missing_its_http_suite() throws IOException {
        Set<String> driven = suitesDriveWhichFeatures();

        List<String> missing;
        try (Stream<Path> specs = Files.list(SPECS)) {
            missing = specs
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".feature"))
                    .filter(name -> !driven.contains(name))
                    .filter(name -> !NO_SUITE_YET.containsKey(name))
                    .sorted()
                    .toList();
        }

        assertThat(missing)
                .as("these specs are never driven over HTTP and nothing says so. Add a"
                        + " RunHttp*Test, or list the file in NO_SUITE_YET with what covers it"
                        + " instead")
                .isEmpty();
    }

    @Test
    @DisplayName("the exemption list does not outlive the gap")
    void an_exemption_disappears_when_the_suite_arrives() throws IOException {
        Set<String> driven = suitesDriveWhichFeatures();

        assertThat(NO_SUITE_YET.keySet().stream().filter(driven::contains).toList())
                .as("these now HAVE an HTTP suite — take them off the list, so the list keeps"
                        + " meaning what it says")
                .isEmpty();
    }

    /** Which feature files the {@code @SelectClasspathResource} annotations actually name. */
    private static Set<String> suitesDriveWhichFeatures() throws IOException {
        try (Stream<Path> suites = Files.list(SUITES)) {
            return suites
                    .filter(path -> path.getFileName().toString().startsWith("RunHttp"))
                    .map(EverySpecHasAnHttpSuiteTest::selectedResource)
                    .filter(name -> !name.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static String selectedResource(Path suite) {
        try {
            String text = Files.readString(suite);
            int at = text.indexOf("@SelectClasspathResource(\"");
            if (at < 0) {
                return "";
            }
            int from = at + "@SelectClasspathResource(\"".length();
            return text.substring(from, text.indexOf('"', from));
        } catch (IOException unreadable) {
            throw new IllegalStateException("could not read " + suite, unreadable);
        }
    }
}
