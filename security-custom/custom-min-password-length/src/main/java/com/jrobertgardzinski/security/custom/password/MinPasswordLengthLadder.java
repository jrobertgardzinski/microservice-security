package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Rung;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import com.jrobertgardzinski.password.config.MinLength;

public final class MinPasswordLengthLadder {

    public static final String KEY = "security.password.policy.min.length";

    private MinPasswordLengthLadder() {
    }

    // this order's choice of rungs: a row an admin writes, over the deployment's property, over the library default
    public static ConfigLadder<Integer> over(LiveConfigPort<Integer> live, RestartConfigPort<Integer> restart) {
        return ConfigLadder.of(KEY, length -> new MinLength(length),
                Rung.live(live), Rung.restart(restart), Rung.rebuild(MinLength.DEFAULT.value()));
    }
}
