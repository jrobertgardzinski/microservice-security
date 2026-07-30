package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.util.Optional;

/**
 * Tracks pending email changes. A pending change remembers the (hashed) token e-mailed to the new
 * address; confirming it with the matching token yields the change once, single-use. Raw tokens are
 * never stored.
 */
public interface EmailChangeRepository {

    void startChange(EmailChange change, VerificationToken token);

    Optional<EmailChange> confirmChange(VerificationToken token);

    /**
     * Drops every pending change that mentions this address, as EITHER end of a move. Called when the
     * account is deleted and when it moves: a change still pending for an address the account no
     * longer owns is a live token pointing at a stranger's account, and the addresses are personal
     * data that must not outlive the account.
     */
    void purge(Email email);
}
