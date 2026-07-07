package com.jrobertgardzinski.security.system.federation;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.FactorType;

public sealed interface FederatedSignInResult {

    record SignedIn(SessionTokens session) implements FederatedSignInResult {}

    /**
     * The provider login proved link #1, but the account has enrolled factors: no session yet. The
     * client presents proofs against {@code ticket} (the same {@code /authenticate/factor} endpoint
     * the password chain uses) until the chain completes.
     */
    record MfaRequired(String ticket, FactorType nextFactor, String challengeData) implements FederatedSignInResult {}

    record Refused(String reason) implements FederatedSignInResult {}
}
