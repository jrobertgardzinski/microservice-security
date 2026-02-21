package com.jrobertgardzinski.email.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LocalPartTest {

    @Test
    void nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> LocalPart.of(null));
    }

    @Test
    void emptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> LocalPart.of(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "j.doe", "j.doe+spam", "user123"})
    void validSucceeds(String value) {
        assertDoesNotThrow(() -> LocalPart.of(value));
    }

    @Test
    void valueReturnsOriginal() {
        assertEquals("j.doe+spam", LocalPart.of("j.doe+spam").value());
    }

    @Test
    void equalsSameValue() {
        assertEquals(LocalPart.of("user"), LocalPart.of("user"));
    }

    @Test
    void notEqualsDifferentValue() {
        assertNotEquals(LocalPart.of("user"), LocalPart.of("other"));
    }
}
