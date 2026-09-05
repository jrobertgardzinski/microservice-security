package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Rung;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.password.application.PasswordPolicyProperties;
import com.jrobertgardzinski.password.config.MinLength;

public final class MinPasswordLengthLadder {

    public static final String KEY = PasswordPolicyProperties.MIN_LENGTH;

    private MinPasswordLengthLadder() {
    }

    // this order's choice of rungs: a row an admin writes, over the deployment's property, over the library default
    public static ConfigLadder<Integer> over(LiveConfigPort<Integer> live, PasswordPolicyProperties properties) {
        return ConfigLadder.of(KEY, length -> new MinLength(length),
                Rung.live(live), Rung.restart(properties.integers()), Rung.rebuild(MinLength.DEFAULT.value()));
    }
}
