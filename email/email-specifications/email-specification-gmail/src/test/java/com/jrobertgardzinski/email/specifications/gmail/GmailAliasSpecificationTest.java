package com.jrobertgardzinski.email.specifications.gmail;

import com.jrobertgardzinski.email.domain.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GmailAliasSpecificationTest {

    private final GmailAliasSpecification spec = new GmailAliasSpecification();

    @ParameterizedTest
    @ValueSource(strings = {"user@gmail.com", "user@googlemail.com", "user@GMAIL.COM"})
    void satisfiedForGmail(String value) {
        assertTrue(spec.isSatisfiedBy(Email.of(value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@yahoo.com", "user@home.pl"})
    void notSatisfiedForNonGmail(String value) {
        assertFalse(spec.isSatisfiedBy(Email.of(value)));
    }

    @Test
    void normalisesDotsAndPlusTags() {
        Email alias    = Email.of("j.doe+spam@gmail.com");
        Email expected = Email.of("jdoe@gmail.com");
        assertEquals(expected, spec.normalise(alias));
    }

    @Test
    void normalisesGooglemailToGmail() {
        Email alias    = Email.of("user@googlemail.com");
        Email expected = Email.of("user@gmail.com");
        assertEquals(expected, spec.normalise(alias));
    }

    @Test
    void normaliseDoesNotChangeNonGmail() {
        Email email = Email.of("user@yahoo.com");
        assertSame(email, spec.normalise(email));
    }

    @Test
    void aliasAndCanonicalNormaliseToSame() {
        Email alias1 = Email.of("j.doe+work@gmail.com");
        Email alias2 = Email.of("jdoe+personal@gmail.com");
        assertEquals(spec.normalise(alias1), spec.normalise(alias2));
    }
}
