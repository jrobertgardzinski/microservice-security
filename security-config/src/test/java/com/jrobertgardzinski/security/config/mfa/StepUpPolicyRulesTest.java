package com.jrobertgardzinski.security.config.mfa;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Config")
@Feature("Step-up policy")
class StepUpPolicyRulesTest {

    @Test
    @DisplayName("a typo in a requirement is rejected at construction, never silently degraded (poz. 22)")
    void rejectsUnknownRequirementValue() {
        // 'FULL_CHAN' is neither NONE, SECOND_FACTORS nor FULL_CHAIN — without validation it would
        // read as "a live session is enough" and quietly drop the guard on delete-account
        assertThrows(IllegalArgumentException.class,
                () -> new StepUpPolicy(Map.of("delete-account", "FULL_CHAN")));
    }

    @Test
    @DisplayName("the known requirement values are accepted")
    void acceptsKnownValues() {
        StepUpPolicy policy = new StepUpPolicy(Map.of(
                "a", StepUpPolicy.NONE, "b", StepUpPolicy.SECOND_FACTORS, "c", StepUpPolicy.FULL_CHAIN));
        assertEquals(StepUpPolicy.NONE, policy.requirementFor("a"));
        assertEquals(StepUpPolicy.SECOND_FACTORS, policy.requirementFor("b"));
        assertEquals(StepUpPolicy.FULL_CHAIN, policy.requirementFor("c"));
    }

    @Test
    @DisplayName("an action nobody configured falls closed to FULL_CHAIN, not open (poz. 1)")
    void unknownActionIsStrictest() {
        StepUpPolicy policy = StepUpPolicy.withDefaults();
        assertEquals(StepUpPolicy.FULL_CHAIN, policy.requirementFor("some-endpoint-that-forgot-to-register"));
    }
}
