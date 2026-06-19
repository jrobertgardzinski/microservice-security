package com.jrobertgardzinski.security.domain.event;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;

public sealed interface BruteForceProtectionEvent {
    record Allowed() implements BruteForceProtectionEvent { }
    record Blocked(AuthenticationBlock authenticationBlock) implements BruteForceProtectionEvent { }
}
