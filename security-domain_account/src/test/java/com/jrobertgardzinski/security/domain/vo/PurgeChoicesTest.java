package com.jrobertgardzinski.security.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The purge map comes straight from the request body and its payload later rides a Kafka record, so
 * it must be bounded — otherwise a single oversized delete request wedges the whole outbox (poz. 14).
 */
class PurgeChoicesTest {

    @Test
    @DisplayName("a huge rule value is rejected")
    void rejectsHugeValue() {
        String twoMegabytes = "x".repeat(2 * 1024 * 1024);
        assertThrows(IllegalArgumentException.class, () -> new PurgeChoices(Map.of("memes", twoMegabytes)));
    }

    @Test
    @DisplayName("too many axes are rejected")
    void rejectsTooManyAxes() {
        Map<String, String> many = IntStream.rangeClosed(0, PurgeChoices.MAX_AXES)
                .boxed().collect(Collectors.toMap(i -> "axis" + i, i -> "DELETE"));
        assertThrows(IllegalArgumentException.class, () -> new PurgeChoices(many));
    }

    @Test
    @DisplayName("a normal wizard choice is accepted")
    void acceptsNormalChoice() {
        assertDoesNotThrow(() -> new PurgeChoices(Map.of("memes", "DELETE", "comments", "ANONYMIZE_AUTHOR")));
        assertDoesNotThrow(PurgeChoices::serviceDefaults);
    }
}
