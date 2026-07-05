package com.jrobertgardzinski.security.system.federation;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;

public sealed interface FederatedSignInResult {

    record SignedIn(SessionTokens session) implements FederatedSignInResult {}

    record Refused(String reason) implements FederatedSignInResult {}
}
