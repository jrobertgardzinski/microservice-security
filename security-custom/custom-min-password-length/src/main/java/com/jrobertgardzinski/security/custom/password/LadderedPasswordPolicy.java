package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.SpecialChars;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;

public final class LadderedPasswordPolicy implements PasswordPolicyInForce {

    private final ConfigLadder<Integer> minLength;

    public LadderedPasswordPolicy(ConfigLadder<Integer> minLength) {
        this.minLength = minLength;
    }

    @Override
    public PasswordPolicy current() {
        // only the length moves; the four other rules stand on the library default
        return PasswordPolicy.defaultsExcept(new MinLength(minLength.resolve()), SpecialChars.DEFAULT);
    }
}
