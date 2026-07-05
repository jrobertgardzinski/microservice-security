package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore;

import java.time.Clock;

/**
 * Public assembly seam for {@link Authentication} and its MFA continuation.
 *
 * <p>{@code Authentication}, {@link ContinueAuthentication} and their collaborators keep package-
 * private constructors on purpose (internals stay hidden). This factory lives in the same package,
 * wires them, and hands back both use cases sharing one chain and one session minter, so a sign-in
 * begun by {@code Authentication} is completed by {@code ContinueAuthentication} identically.
 */
public final class AuthenticationFactory {

    /** The two halves of a sign-in: start (through link #1) and continue (through the factor chain). */
    public record AuthenticationUseCases(Authentication authentication,
                                         ContinueAuthentication continueAuthentication) {}

    private AuthenticationFactory() {
    }

    public static AuthenticationUseCases assemble(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            RejectedAuthenticationRepository rejectedAuthenticationRepository,
            AuthenticationBlockRepository authenticationBlockRepository,
            AuthorizationDataRepository authorizationDataRepository,
            HashAlgorithmPort hashAlgorithmPort,
            BruteForceConfig bruteForceConfig,
            SessionTokensConfig sessionTokensConfig,
            Clock clock,
            BlockDurationPolicy blockDurationPolicy,
            AccessTokenMint accessTokenMint,
            EnrolledFactorRepository enrolledFactorRepository,
            com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain,
            PendingAuthenticationStore pendingAuthenticationStore) {

        var bruteForceGuard = new _BruteForceGuard(
                rejectedAuthenticationRepository, authenticationBlockRepository,
                clock, bruteForceConfig, blockDurationPolicy);
        var verifyCredentials = new _VerifyCredentials(userRepository, hashAlgorithmPort);
        var requireVerifiedEmail = new _RequireVerifiedEmail(emailVerificationRepository);
        var generateSession = new _GenerateSession(authorizationDataRepository, clock, sessionTokensConfig, accessTokenMint);
        var cleanBruteForceRecords = new _CleanBruteForceRecords(
                rejectedAuthenticationRepository, authenticationBlockRepository);
        var updateBruteForceRecords = new _UpdateBruteForceRecords(rejectedAuthenticationRepository, clock);

        var authentication = new Authentication(
                bruteForceGuard, verifyCredentials, requireVerifiedEmail, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords,
                enrolledFactorRepository, mfaChain, pendingAuthenticationStore);
        var continueAuthentication = new ContinueAuthentication(
                pendingAuthenticationStore, mfaChain, generateSession, clock);

        return new AuthenticationUseCases(authentication, continueAuthentication);
    }
}
