package com.jrobertgardzinski;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A law, in the family of {@link StoresWithADeadlineEvictThemTest}: the config ports are named
 * after WHAT CHANGING THE VALUE COSTS (restart, live), and the compiler cannot verify that
 * promise — {@code RestartConfigPort} accepts any lambda, a SELECT included. So the promise is
 * verified here, by reading the adapters' sources.
 *
 * <p>The mistake is dangerous in both directions. A database read wired as a RESTART port makes
 * the value MORE dynamic than declared: it changes without the restart everyone was promised,
 * and nobody can reason about when configuration takes effect any more. An Environment read
 * wired as a LIVE port makes the value LESS dynamic than declared: the rung is forever stale
 * and the administrator's change quietly never arrives. Both are the declaration lying; both
 * are caught by the same scan.
 */
class ConfigPortsTellTheTruthTest {

    private static final Path SOURCES = Path.of("src/main/java/com/jrobertgardzinski");

    /** Anything that smells of the database in a class claiming restart-bound semantics. */
    private static final List<String> DATABASE_SMELLS = List.of(
            "javax.sql", "io.micronaut.data", "DataSource", "JdbcRepository", "DriverManager");

    /** Start-time property machinery in a class claiming live semantics. */
    private static final List<String> START_TIME_SMELLS = List.of(
            "io.micronaut.context.env.Environment", "@Value(");

    @Test
    void restart_adapters_do_not_read_the_database() throws IOException {
        List<String> lying = adaptersOf("RestartConfigPort").stream()
                .filter(file -> DATABASE_SMELLS.stream().anyMatch(read(file)::contains))
                .map(file -> file.getFileName().toString())
                .toList();

        assertEquals(List.of(), lying,
                "these classes promise restart-bound semantics but read the database - the value"
                        + " changes WITHOUT the restart everyone was promised: " + lying);
    }

    @Test
    void live_adapters_do_not_read_the_environment() throws IOException {
        List<String> lying = adaptersOf("LiveConfigPort").stream()
                .filter(file -> START_TIME_SMELLS.stream().anyMatch(read(file)::contains))
                .map(file -> file.getFileName().toString())
                .toList();

        assertEquals(List.of(), lying,
                "these classes promise live semantics but read start-time properties - the rung"
                        + " is forever stale and an administrator's change never arrives: " + lying);
    }

    private static List<Path> adaptersOf(String port) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> read(file).contains("implements " + port))
                    .toList();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read " + file, unreadable);
        }
    }
}
