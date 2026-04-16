package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    void notNull() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Email.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\"", "some-random_string text"})
    void failingRegex(String value) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Email.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@gmail.com", "andrzej@wp.pl"})
    void success(String value) {
        Assertions.assertDoesNotThrow(() -> Email.of(value));
    }

}