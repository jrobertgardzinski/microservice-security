package com.jrobertgardzinski.email.specifications.blockeddomain;

import com.jrobertgardzinski.email.domain.Email;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockedDomainSpecificationTest {

    private final BlockedDomainSpecification spec =
            new BlockedDomainSpecification(Set.of("evil.com", "spam.org"));

    @Test
    void satisfiedWhenDomainNotBlocked() {
        assertTrue(spec.isSatisfiedBy(Email.of("user@gmail.com")));
    }

    @Test
    void notSatisfiedWhenDomainBlocked() {
        assertFalse(spec.isSatisfiedBy(Email.of("user@evil.com")));
    }

    @Test
    void blockedDomainCaseInsensitive() {
        assertFalse(spec.isSatisfiedBy(Email.of("user@EVIL.COM")));
    }

    @Test
    void emptyBlocklistAllowsEverything() {
        BlockedDomainSpecification empty = new BlockedDomainSpecification(Set.of());
        assertTrue(empty.isSatisfiedBy(Email.of("user@anything.com")));
    }
}
