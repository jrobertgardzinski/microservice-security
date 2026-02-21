package com.jrobertgardzinski.salt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaltConfigTest {

    @Test
    void defaultByteLength() {
        SaltConfig config = SaltConfig.builder().build();
        assertEquals(16, config.byteLength());
    }

    @Test
    void customByteLength() {
        SaltConfig config = SaltConfig.builder().byteLength(32).build();
        assertEquals(32, config.byteLength());
    }

    @Test
    void tooSmallByteLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SaltConfig.builder().byteLength(7).build());
    }

    @Test
    void minimumByteLengthAccepted() {
        assertDoesNotThrow(() -> SaltConfig.builder().byteLength(8).build());
    }
}
