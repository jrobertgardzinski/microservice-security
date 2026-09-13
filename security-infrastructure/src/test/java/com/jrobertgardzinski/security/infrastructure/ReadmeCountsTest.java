package com.jrobertgardzinski.security.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Readme is a landing page people arrive at from a QR code, so the one thing it must not do is
 * overstate the work. It said the specs "today run from the application layer" long after they ran
 * from three, and claimed every spec runs at the HTTP layer when sixteen of nineteen do.
 *
 * <p>Nothing there was dishonest — the sentences simply aged. Which is the case for pinning the
 * numbers rather than fixing them again: a claim about how much is covered is worth exactly as much
 * as the thing that notices when it stops being true.
 */
class ReadmeCountsTest {

    private static final Path README = Path.of("../Readme.md");
    private static final Path SPECS = Path.of("../specs");
    private static final Path HTTP_SUITES = Path.of("src/test/java/com/jrobertgardzinski/security/infrastructure");
    private static final Path APPLICATION_SUITE = Path.of(
            "../security-application/src/test/java/com/jrobertgardzinski/security/application/RunCucumberTest.java");

    @Test
    @DisplayName("the Readme's coverage sentence counts what is really there")
    void the_landing_page_does_not_overstate() throws IOException {
        String readme = Files.readString(README);

        assertThat(readme)
                .as("the Readme names %d specs", specs())
                .contains("Of the " + specs() + " specs,")
                .contains(httpSuites() + " run at the HTTP layer")
                .contains(browserSpecs() + " in a real browser")
                .contains(applicationSpecs() + " at the application layer");
    }

    private static long specs() throws IOException {
        try (Stream<Path> files = Files.list(SPECS)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".feature")).count();
        }
    }

    /** A feature reaches the browser by carrying {@code @ui} — see specs/README.md. */
    private static long browserSpecs() throws IOException {
        try (Stream<Path> files = Files.list(SPECS)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".feature"))
                    .filter(ReadmeCountsTest::carriesTheUiTag)
                    .count();
        }
    }

    private static long httpSuites() throws IOException {
        try (Stream<Path> files = Files.list(HTTP_SUITES)) {
            return files.filter(path -> path.getFileName().toString().startsWith("RunHttp")).count();
        }
    }

    private static long applicationSpecs() throws IOException {
        return Files.readString(APPLICATION_SUITE).lines()
                .filter(line -> line.contains("@SelectClasspathResource("))
                .count();
    }

    private static boolean carriesTheUiTag(Path feature) {
        try {
            return Files.readString(feature).lines().anyMatch(line -> line.trim().equals("@ui"));
        } catch (IOException unreadable) {
            throw new IllegalStateException("could not read " + feature, unreadable);
        }
    }
}
