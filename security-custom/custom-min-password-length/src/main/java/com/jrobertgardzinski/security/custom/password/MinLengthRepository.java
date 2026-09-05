package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.password.config.MinLength;

public interface MinLengthRepository {
    void save(MinLength minLength);
}
