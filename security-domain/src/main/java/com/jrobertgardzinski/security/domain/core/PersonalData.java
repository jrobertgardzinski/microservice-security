package com.jrobertgardzinski.security.domain.core;

import com.jrobertgardzinski.email.domain.Email;

/**
 * What one area keeps about a person, keyed by their address. Closing an account erases it from
 * every area; changing the address moves it. The account area holds a list of these and does not
 * know which areas there are — a new area that keeps something about a person adds one.
 */
public interface PersonalData {

    void erase(Email email);

    /** Carries what this area keeps from {@code from} to {@code to}, or drops what must not follow. */
    void move(Email from, Email to);
}
