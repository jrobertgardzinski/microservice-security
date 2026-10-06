package com.jrobertgardzinski.security.domain.core;

import com.jrobertgardzinski.email.domain.Email;

/** Whether an address has been proven to belong to its account — the mailbox area's verdict. */
public interface VerifiedAddresses {

    boolean isVerified(Email email);

    void markVerified(Email email);
}
