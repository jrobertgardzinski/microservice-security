package com.jrobertgardzinski.email.specifications.rfc;

import com.jrobertgardzinski.email.domain.Email;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfcFormatSpecificationTest {

    private final RfcFormatSpecification spec = new RfcFormatSpecification();

    @ParameterizedTest
    @ValueSource(strings = {
            "user@gmail.com",
            "j.doe+spam@gmail.com",
            "user123@home.pl",
            "user@mail.google.com"
    })
    void validEmails(String value) {
        assertTrue(spec.isSatisfiedBy(Email.of(value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user @gmail.com",
            "user@-gmail.com",
            "user@gmail..com"
    })
    void invalidEmails(String value) {
        assertFalse(spec.isSatisfiedBy(Email.of(value)));
    }
}
