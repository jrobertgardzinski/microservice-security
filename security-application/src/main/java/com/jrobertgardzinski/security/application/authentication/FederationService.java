package com.jrobertgardzinski.security.application.authentication;

import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.session.SessionTokens;
import com.jrobertgardzinski.security.domain.mfa.FactorType;
import com.jrobertgardzinski.security.domain.core.ProviderIdentity;
import com.jrobertgardzinski.security.system.authentication.FederatedSignIn;
import com.jrobertgardzinski.security.system.authentication.FederatedSignInResult;

/**
 * Signing in with an identity a provider vouched for. The dance with the provider — state, PKCE,
 * the token exchange — is the protocol's and stays with the infrastructure; what arrives here is
 * the identity it proved.
 */
public final class FederationService {

    private final FederatedSignIn federatedSignIn;
    private final TransactionBoundary transactionBoundary;

    public FederationService(FederatedSignIn federatedSignIn, TransactionBoundary transactionBoundary) {
        this.federatedSignIn = federatedSignIn;
        this.transactionBoundary = transactionBoundary;
    }

    public Outcome signIn(ProviderIdentity identity) {
        return switch (transactionBoundary.execute(() -> federatedSignIn.execute(identity))) {
            case FederatedSignInResult.SignedIn signedIn -> new Outcome.SignedIn(signedIn.session());
            case FederatedSignInResult.MfaRequired mfa ->
                    new Outcome.MfaRequired(mfa.ticket(), mfa.nextFactor(), mfa.challengeData());
            case FederatedSignInResult.Refused refused -> new Outcome.Refused(refused.reason());
        };
    }

    public sealed interface Outcome {

        record SignedIn(SessionTokens session) implements Outcome {}

        /** The account has enrolled factors: the chain is finished like a password sign-in's. */
        record MfaRequired(String ticket, FactorType nextFactor, String challengeData) implements Outcome {}

        record Refused(String reason) implements Outcome {}
    }
}
