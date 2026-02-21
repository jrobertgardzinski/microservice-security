package com.jrobertgardzinski.token.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionConfigTest {

    @Test
    void defaultsAreApplied() {
        SessionConfig config = SessionConfig.builder().build();

        assertEquals(48, config.refreshTokenValidityHours());
        assertEquals(48, config.accessTokenValidityHours());
    }

    @Test
    void customValuesOverrideDefaults() {
        SessionConfig config = SessionConfig.builder()
                .refreshTokenValidityHours(720)
                .accessTokenValidityHours(1)
                .build();

        assertEquals(720, config.refreshTokenValidityHours());
        assertEquals(1, config.accessTokenValidityHours());
    }

    @Test
    void zeroRefreshHoursThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionConfig.builder().refreshTokenValidityHours(0).build());
    }

    @Test
    void zeroAccessHoursThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionConfig.builder().accessTokenValidityHours(0).build());
    }
}
