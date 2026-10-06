package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.core.FailedSignIns;
import com.jrobertgardzinski.security.domain.core.SecondFactors;
import com.jrobertgardzinski.security.domain.core.Sessions;
import com.jrobertgardzinski.security.domain.core.VerifiedAddresses;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.security.config.authentication.BruteForceConfig;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.core.UserRepository;

import java.time.Clock;

/**
 * Public assembly seam for {@link Authentication}.
 *
 * <p>{@code Authentication} and its collaborators keep package-private constructors on purpose
 * (internals stay hidden); this factory lives in the same package and wires them. What a sign-in
 * needs from other areas arrives through ports in core — the session it opens, the verified
 * address it demands, the factor chain it begins and the failures it records — so the factor step
 * that completes a sign-in ({@code ContinueAuthentication}, in mfa) opens the same kind of session
 * through the same port.
 */
public final class AuthenticationFactory {

    /** The two halves of a sign-in: start (through link #1) and continue (through the factor chain). */
    private AuthenticationFactory() {
    }

    public static Authentication assemble(
            UserRepository userRepository,
            VerifiedAddresses verifiedAddresses,
            RejectedAuthenticationRepository rejectedAuthenticationRepository,
            AuthenticationBlockRepository authenticationBlockRepository,
            HashAlgorithmPort hashAlgorithmPort,
            BruteForceConfig bruteForceConfig,
            Clock clock,
            BlockDurationPolicy blockDurationPolicy,
            Sessions sessions,
            FailedSignIns failedSignIns,
            SecondFactors secondFactors) {
        var bruteForceGuard = new _BruteForceGuard(
                rejectedAuthenticationRepository, authenticationBlockRepository,
                clock, bruteForceConfig, blockDurationPolicy);
        var verifyCredentials = new _VerifyCredentials(userRepository, hashAlgorithmPort);
        var cleanBruteForceRecords = new _CleanBruteForceRecords(rejectedAuthenticationRepository);
        return new Authentication(bruteForceGuard, verifyCredentials, verifiedAddresses, sessions,
                cleanBruteForceRecords, failedSignIns, secondFactors);
    }
}
