package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.FactorType;

public sealed interface AuthenticationResult {
    record Authenticated(SessionTokens session) implements AuthenticationResult {}
    record Rejected() implements AuthenticationResult {}
    record Blocked(AuthenticationBlock authenticationBlock) implements AuthenticationResult {}
    /** Credentials were correct, but the e-mail address has not been verified yet. */
    record EmailNotVerified() implements AuthenticationResult {}
    /**
     * Link #1 (the password) passed, but the user has enrolled factors: no session yet. The client
     * presents proofs against {@code ticket} until the chain completes; the first factor's
     * challenge (if any) has already been issued.
     */
    record MfaRequired(String ticket, FactorType nextFactor) implements AuthenticationResult {}
}
