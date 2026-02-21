package com.jrobertgardzinski.password.factory;

import com.jrobertgardzinski.password.domain.PasswordPolicy;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

/**
 * Creates PlaintextPassword instances while enforcing the active policy.
 * Single entry point for producing PlaintextPassword objects in application code.
 */
public class PasswordFactory {

    private final PasswordPolicy policy;

    public PasswordFactory(PasswordPolicy policy) {
        this.policy = policy;
    }

    public PlaintextPassword create(String rawPassword) {
        return PlaintextPassword.of(rawPassword, policy);
    }
}
