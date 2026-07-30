package com.jrobertgardzinski;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An elevation is keyed by the access token AND the action it was proven for (poz. 1): an elevation
 * earned for one action must never satisfy another, and it is one-shot.
 */
class InMemorySessionElevationTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("an elevation for one action does not unlock another")
    void elevationIsScopedToItsAction() {
        InMemorySessionElevation elevation = new InMemorySessionElevation(clock, 5);
        elevation.elevate("access-token", "admin-reset");

        assertFalse(elevation.consume("access-token", "delete-account"),
                "an admin-reset elevation must NOT satisfy delete-account");
        assertTrue(elevation.consume("access-token", "admin-reset"),
                "it satisfies the action it was minted for");
    }

    @Test
    @DisplayName("consuming an elevation clears it (one-shot)")
    void elevationIsOneShot() {
        InMemorySessionElevation elevation = new InMemorySessionElevation(clock, 5);
        elevation.elevate("access-token", "delete-account");

        assertTrue(elevation.consume("access-token", "delete-account"));
        assertFalse(elevation.consume("access-token", "delete-account"), "a second consume finds nothing");
    }
}
