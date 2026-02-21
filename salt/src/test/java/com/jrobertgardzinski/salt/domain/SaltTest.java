package com.jrobertgardzinski.salt.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SaltTest {

    @Test
    void nullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Salt(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void blankValueThrows(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Salt(value));
    }

    @Test
    void validValueSucceeds() {
        assertDoesNotThrow(() -> new Salt("someSaltValue"));
    }

    @Test
    void generateProducesNonBlankValue() {
        Salt salt = Salt.generate(16);
        assertFalse(salt.value().isBlank());
    }

    @Test
    void generateProducesUniqueValues() {
        Salt a = Salt.generate(16);
        Salt b = Salt.generate(16);
        assertNotEquals(a, b);
    }

    @Test
    void generateRespectsByteLength() {
        Salt salt = Salt.generate(32);
        // Base64 of 32 bytes = ceil(32/3)*4 = 44 characters
        assertEquals(44, salt.value().length());
    }
}
