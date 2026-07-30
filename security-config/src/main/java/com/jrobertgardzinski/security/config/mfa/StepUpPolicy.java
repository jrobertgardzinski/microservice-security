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
        // a typo in a per-action requirement (e.g. FULL_CHAN) must not silently degrade to "a live
        // session is enough" — reject unknown values at start, the way parseParticipants does in
        // offboarding, so a misconfiguration fails loudly instead of dropping the guard.
        byAction.forEach((action, requirement) -> {
            if (!NONE.equals(requirement) && !SECOND_FACTORS.equals(requirement)
                    && !FULL_CHAIN.equals(requirement)) {
                throw new IllegalArgumentException("step-up requirement for '" + action
                        + "' must be one of NONE, SECOND_FACTORS, FULL_CHAIN but was '" + requirement + "'");
            }
        });
    }

    /**
     * The requirement for an action. An action nobody configured is treated as the strictest
     * ({@code FULL_CHAIN}): a new sensitive endpoint that forgot to register its policy must fail
     * closed (re-prove everything), never open (a live session is enough).
     */
    public String requirementFor(String action) {
        return byAction.getOrDefault(action, FULL_CHAIN);
    }

    public static StepUpPolicy withDefaults() {
        return new StepUpPolicy(Map.of("delete-account", FULL_CHAIN, "change-password", SECOND_FACTORS));
    }
}
