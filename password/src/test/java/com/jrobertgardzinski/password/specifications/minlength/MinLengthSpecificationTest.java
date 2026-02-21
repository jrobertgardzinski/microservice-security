package com.jrobertgardzinski.password.specifications.minlength;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.domain.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyAdapter;
import com.jrobertgardzinski.password.policy.PasswordPolicyConfig;
import com.jrobertgardzinski.password.specifications.MinLengthSpecification;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MinLengthSpecificationTest {

    @Test
    void constructorRejectsTooSmallMinLength() {
        assertThrows(IllegalArgumentException.class, () -> new MinLengthSpecification(4));
    }

    @Test
    void constructorAcceptsMinLengthOfFive() {
        assertDoesNotThrow(() -> new MinLengthSpecification(5));
    }

    @Test
    void passwordMeetingLengthPasses() {
        MinLengthSpecification spec = new MinLengthSpecification(8);
        PlaintextPassword pw = passwordOf("12345678");
        assertEquals(Optional.empty(), spec.check(pw));
    }

    @Test
    void passwordBelowLengthFails() {
        MinLengthSpecification spec = new MinLengthSpecification(8);
        PlaintextPassword pw = passwordOf("short");
        assertTrue(spec.check(pw).isPresent());
    }

    /** Helper: creates a PlaintextPassword bypassing policy (only length check needed here). */
    private static PlaintextPassword passwordOf(String raw) {
        PasswordPolicy noRules = new PasswordPolicyAdapter(
                PasswordPolicyConfig.builder()
                        .minLength(5)
                        .requireLowercase(false)
                        .requireUppercase(false)
                        .requireDigit(false)
                        .noSpecialChars()
                        .build()
        );
        return PlaintextPassword.of(raw, noRules);
    }
}
