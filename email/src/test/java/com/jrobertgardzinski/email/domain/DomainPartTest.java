package com.jrobertgardzinski.email.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DomainPartTest {

    @Test
    void nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> DomainPart.of(null));
    }

    @Test
    void emptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> DomainPart.of(""));
    }

    @Test
    void missingDotThrows() {
        assertThrows(IllegalArgumentException.class, () -> DomainPart.of("localhost"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gmail.com", "home.pl", "mail.google.com", "booking.co.uk"})
    void validSucceeds(String value) {
        assertDoesNotThrow(() -> DomainPart.of(value));
    }

    @Test
    void normalizesToLowercase() {
        assertEquals("gmail.com", DomainPart.of("GMAIL.COM").value());
    }

    @Test
    void equalsCaseInsensitive() {
        assertEquals(DomainPart.of("GMAIL.COM"), DomainPart.of("gmail.com"));
    }
}
