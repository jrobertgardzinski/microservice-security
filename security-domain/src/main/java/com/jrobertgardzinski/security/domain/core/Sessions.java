package com.jrobertgardzinski.security.domain.core;

import com.jrobertgardzinski.email.domain.Email;

/**
 * A person's sessions, as the other areas need them: a sign-in opens one, and a change that should
 * outlive no session — a new password, a new address, a closed account — ends them all. How a
 * session is kept and rotated is the session area's own business.
 */
public interface Sessions {

    IssuedSession open(Email email);

    void endAll(Email email);
}
