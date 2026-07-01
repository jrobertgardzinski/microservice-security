package com.jrobertgardzinski.security.domain.repository;

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
}
