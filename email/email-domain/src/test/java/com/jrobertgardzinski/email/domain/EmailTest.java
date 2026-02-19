package com.jrobertgardzinski.email.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Email.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void blankThrows(String value) {
        assertThrows(IllegalArgumentException.class, () -> Email.of(value));
    }

    @Test
    void missingAtThrows() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("usergmail.com"));
    }

    @Test
    void multipleAtThrows() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("user@@gmail.com"));
    }

    @Test
    void emptyLocalThrows() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("@gmail.com"));
    }

    @Test
    void emptyDomainThrows() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("user@"));
    }

    @Test
    void domainWithoutDotThrows() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("user@localhost"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@gmail.com", "j.doe+spam@gmail.com", "user@home.pl", "user@booking.co.uk"})
    void validSucceeds(String value) {
        assertDoesNotThrow(() -> Email.of(value));
    }

    @Test
    void localPreservesCase() {
        assertEquals("J.Doe", Email.of("J.Doe@gmail.com").local().value());
    }

    @Test
    void domainNormalizesToLowercase() {
        assertEquals("gmail.com", Email.of("user@GMAIL.COM").domain().value());
    }

    @Test
    void valueIsDerived() {
        assertEquals("user@gmail.com", Email.of("user@GMAIL.COM").value());
    }

    @Test
    void equalsSameAddress() {
        assertEquals(Email.of("user@gmail.com"), Email.of("user@gmail.com"));
    }

    @Test
    void equalsCaseInsensitiveDomain() {
        assertEquals(Email.of("user@GMAIL.COM"), Email.of("user@gmail.com"));
    }

    @Test
    void notEqualsDifferentLocal() {
        assertNotEquals(Email.of("alice@gmail.com"), Email.of("bob@gmail.com"));
    }
}
