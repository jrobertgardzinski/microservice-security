package com.jrobertgardzinski.security.system.settings;

import com.jrobertgardzinski.password.security.config.MinLength;

public interface MinPasswordLengthStore {

    void save(MinLength minLength);
}
