package com.jrobertgardzinski.security.system.passwordpolicy;

import com.jrobertgardzinski.password.config.MinLength;

/** The write side of the live level, save alone on purpose: the ladder is the one that reads. */
public interface MinLengthRepository {
    void save(MinLength minLength);
}
