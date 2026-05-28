package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;

public sealed interface AuthenticationResult {
    record Passed(SessionTokens session) implements AuthenticationResult {}
    record Failed() implements AuthenticationResult {}
    record Blocked(AuthenticationBlock authenticationBlock) implements AuthenticationResult {}

}
