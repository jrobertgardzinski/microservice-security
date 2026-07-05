package com.jrobertgardzinski.security.config.mfa;

import java.util.Map;

/**
 * How much a sensitive action must be re-proven — step-up authentication. Per action, one of
 * {@code NONE} (a live session is enough), {@code SECOND_FACTORS} (re-pass the enrolled factors)
 * or {@code FULL_CHAIN} (re-enter the password AND re-pass the factors). Config, overridable per
 * deployment ({@code security.step-up.<action>}); keyed by an action name so the layer stays
 * independent of the endpoints. Defaults: deleting an account is FULL_CHAIN, changing a password is
 * SECOND_FACTORS (the old password is already required inline there).
 */
public record StepUpPolicy(Map<String, String> byAction) {

    public static final String NONE = "NONE";
    public static final String SECOND_FACTORS = "SECOND_FACTORS";
    public static final String FULL_CHAIN = "FULL_CHAIN";

    public StepUpPolicy {
        byAction = Map.copyOf(byAction);
    }

    public String requirementFor(String action) {
        return byAction.getOrDefault(action, SECOND_FACTORS);
    }

    public static StepUpPolicy withDefaults() {
        return new StepUpPolicy(Map.of("delete-account", FULL_CHAIN, "change-password", SECOND_FACTORS));
    }
}
