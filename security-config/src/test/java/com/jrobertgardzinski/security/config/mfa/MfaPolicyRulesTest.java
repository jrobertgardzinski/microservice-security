package com.jrobertgardzinski.security.config.mfa;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Epic("Config")
@Feature("MFA role floor")
class MfaPolicyRulesTest {

    private final MfaPolicy policy = MfaPolicy.withDefaults();

    @Test
    @DisplayName("the floor is the strictest across the caller's roles")
    void strictestAcrossRoles() {
        assertEquals(1, policy.requiredFactorCount(Set.of("USER")));
        assertEquals(2, policy.requiredFactorCount(Set.of("USER", "MODERATOR")));
        assertEquals(3, policy.requiredFactorCount(Set.of("USER", "MODERATOR", "ADMIN")));
    }

    @Test
    @DisplayName("an unknown role asks for the USER minimum, not zero")
    void unknownRoleDefaultsToOne() {
        assertEquals(1, policy.requiredFactorCount(Set.of("WHATEVER")));
    }
}
