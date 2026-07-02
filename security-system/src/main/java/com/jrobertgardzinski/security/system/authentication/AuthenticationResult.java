package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;

public sealed interface AuthenticationResult {
    record Authenticated(SessionTokens session) implements AuthenticationResult {}
    record Rejected() implements AuthenticationResult {}
    record Blocked(AuthenticationBlock authenticationBlock) implements AuthenticationResult {}
    /** Credentials were correct, but the e-mail address has not been verified yet. */
    record EmailNotVerified() implements AuthenticationResult {}

}
