package com.jrobertgardzinski.security.domain.authentication;


public sealed interface BruteForceProtectionEvent {
    record Allowed() implements BruteForceProtectionEvent { }
    record Blocked(AuthenticationBlock authenticationBlock) implements BruteForceProtectionEvent { }
}
