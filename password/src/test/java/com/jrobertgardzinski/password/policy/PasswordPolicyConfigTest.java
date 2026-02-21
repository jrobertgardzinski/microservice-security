package com.jrobertgardzinski.password.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyConfigTest {

    @Test
    void defaultsAreApplied() {
        PasswordPolicyConfig config = PasswordPolicyConfig.builder().build();

        assertEquals(12, config.minLength());
        assertTrue(config.requireLowercase());
        assertTrue(config.requireUppercase());
        assertTrue(config.requireDigit());
        assertEquals("#?!", config.specialChars());
    }

    @Test
    void builderOverridesDefaults() {
        PasswordPolicyConfig config = PasswordPolicyConfig.builder()
                .minLength(8)
                .requireDigit(false)
                .noSpecialChars()
                .build();

        assertEquals(8, config.minLength());
        assertFalse(config.requireDigit());
        assertTrue(config.specialChars().isBlank());
    }
}
