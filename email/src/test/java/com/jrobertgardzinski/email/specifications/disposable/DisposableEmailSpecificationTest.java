package com.jrobertgardzinski.email.specifications.disposable;

import com.jrobertgardzinski.email.domain.Email;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposableEmailSpecificationTest {

    private final DisposableEmailSpecification spec =
            new DisposableEmailSpecification(Set.of("mailinator.com", "guerrillamail.com", "tempmail.com"));

    @Test
    void satisfiedWhenNotDisposable() {
        assertTrue(spec.isSatisfiedBy(Email.of("user@gmail.com")));
    }

    @Test
    void notSatisfiedWhenDisposable() {
        assertFalse(spec.isSatisfiedBy(Email.of("user@mailinator.com")));
    }

    @Test
    void disposableDomainCaseInsensitive() {
        assertFalse(spec.isSatisfiedBy(Email.of("user@MAILINATOR.COM")));
    }
}
