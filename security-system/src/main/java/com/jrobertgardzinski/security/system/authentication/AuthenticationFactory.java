package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;

import java.time.Clock;

/**
 * Public assembly seam for {@link Authentication}.
 *
 * <p>{@code Authentication} and its collaborators keep package-private constructors on purpose
 * (internals stay hidden). This factory lives in the same package, so it can wire them, and
 * exposes a single public entry point for callers outside the package (DI / tests).
 */
public final class AuthenticationFactory {

    private AuthenticationFactory() {
    }

    public static Authentication create(
            UserRepository userRepository,
            RejectedAuthenticationRepository rejectedAuthenticationRepository,
            AuthenticationBlockRepository authenticationBlockRepository,
            AuthorizationDataRepository authorizationDataRepository,
            HashAlgorithmPort hashAlgorithmPort,
            BruteForceConfig bruteForceConfig,
            SessionTokensConfig sessionTokensConfig,
            Clock clock,
            BlockDurationPolicy blockDurationPolicy) {

        var bruteForceGuard = new _BruteForceGuard(
                rejectedAuthenticationRepository, authenticationBlockRepository,
                clock, bruteForceConfig, blockDurationPolicy);
        var verifyCredentials = new _VerifyCredentials(userRepository, hashAlgorithmPort);
        var generateSession = new _GenerateSession(authorizationDataRepository, clock, sessionTokensConfig);
        var cleanBruteForceRecords = new _CleanBruteForceRecords(
                rejectedAuthenticationRepository, authenticationBlockRepository);
        var updateBruteForceRecords = new _UpdateBruteForceRecords(rejectedAuthenticationRepository, clock);

        return new Authentication(
                bruteForceGuard, verifyCredentials, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords);
    }
}
