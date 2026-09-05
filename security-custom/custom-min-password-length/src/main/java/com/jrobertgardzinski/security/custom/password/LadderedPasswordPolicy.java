package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;

public final class LadderedPasswordPolicy implements PasswordPolicyInForce {

    private final ConfigLadder<Integer> minLength;
    private final PasswordPolicyInForce deployment;

    public LadderedPasswordPolicy(ConfigLadder<Integer> minLength, PasswordPolicyInForce deployment) {
        this.minLength = minLength;
        this.deployment = deployment;
    }

    @Override
    public PasswordPolicy current() {
        // only the length has a live level; the four other rules are whatever the deployment says
        PasswordPolicy base = deployment.current();
        return new PasswordPolicy(new MinLength(minLength.resolve()), base.specialChars(),
                base.requiresUppercase(), base.requiresLowercase(), base.requiresDigit());
    }
}
