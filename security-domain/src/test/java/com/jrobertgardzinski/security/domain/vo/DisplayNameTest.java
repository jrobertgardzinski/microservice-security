package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayNameTest {

    @Test
    void keeps_one_character_and_the_domain() {
        assertEquals("a***@example.com", DisplayName.of(Email.of("alice@example.com")).value());
        assertEquals("x***@example.com", DisplayName.of(Email.of("x@example.com")).value());
    }
}
