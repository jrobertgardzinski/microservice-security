package com.jrobertgardzinski.password.domain;

import com.jrobertgardzinski.password.policy.PasswordPolicyAdapter;
import com.jrobertgardzinski.password.policy.PasswordPolicyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaintextPasswordTest {

    private static final PasswordPolicy PERMISSIVE =
            new PasswordPolicyAdapter(
                    PasswordPolicyConfig.builder()
                            .minLength(5)
                            .requireLowercase(false)
                            .requireUppercase(false)
                            .requireDigit(false)
                            .noSpecialChars()
                            .build()
            );

    private static final PasswordPolicy DEFAULT = new PasswordPolicyAdapter();

    @Test
    void validPasswordSucceeds() {
        assertDoesNotThrow(() -> PlaintextPassword.of("hello", PERMISSIVE));
    }

    @Test
    void tooShortThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PlaintextPassword.of("hi", PERMISSIVE));
    }

    @Test
    void defaultPolicyRequiresAllRules() {
        assertThrows(IllegalArgumentException.class,
                () -> PlaintextPassword.of("simple", DEFAULT));
    }

    @Test
    void strongPasswordPassesDefaultPolicy() {
        assertDoesNotThrow(() -> PlaintextPassword.of("Str0ng#Pass!", DEFAULT));
    }

    @Test
    void equalsSameValue() {
        PlaintextPassword a = PlaintextPassword.of("hello", PERMISSIVE);
        PlaintextPassword b = PlaintextPassword.of("hello", PERMISSIVE);
        assertEquals(a, b);
    }

    @Test
    void notEqualsDifferentValue() {
        PlaintextPassword a = PlaintextPassword.of("hello", PERMISSIVE);
        PlaintextPassword b = PlaintextPassword.of("world", PERMISSIVE);
        assertNotEquals(a, b);
    }

    @Test
    void toStringRedactsValue() {
        PlaintextPassword p = PlaintextPassword.of("hello", PERMISSIVE);
        assertFalse(p.toString().contains("hello"));
    }
}
