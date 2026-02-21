package com.jrobertgardzinski.token.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TokenTest {

    // --- Token ---

    @Test
    void nullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Token(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void blankValueThrows(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Token(value));
    }

    @Test
    void randomProducesNonBlankValue() {
        assertFalse(Token.random().value().isBlank());
    }

    @Test
    void randomProducesUniqueValues() {
        assertNotEquals(Token.random(), Token.random());
    }

    // --- TokenExpiration ---

    @Test
    void nullExpirationThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TokenExpiration(null));
    }

    @Test
    void validInHoursProducesCorrectExpiration() {
        Clock fixed = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC);
        TokenExpiration exp = TokenExpiration.validInHours(2, fixed);
        assertEquals(LocalDateTime.parse("2025-01-01T12:00:00"), exp.value());
    }

    // --- RefreshTokenExpiration ---

    @Test
    void hasExpiredReturnsTrueWhenPast() {
        Clock pastClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        TokenExpiration expired = TokenExpiration.validInHours(1, pastClock);
        RefreshTokenExpiration rte = new RefreshTokenExpiration(expired);

        Clock nowClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertTrue(rte.hasExpired(nowClock));
    }

    @Test
    void hasExpiredReturnsFalseWhenFuture() {
        Clock nowClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        TokenExpiration future = TokenExpiration.validInHours(24, nowClock);
        RefreshTokenExpiration rte = new RefreshTokenExpiration(future);

        assertTrue(!rte.hasExpired(nowClock));
    }
}
