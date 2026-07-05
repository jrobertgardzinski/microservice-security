package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.event.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore;

import java.util.List;

public class Authentication {
    private final _BruteForceGuard bruteForceGuard;
    private final _VerifyCredentials verifyCredentials;
    private final _RequireVerifiedEmail requireVerifiedEmail;
    private final _GenerateSession generateSession;
    private final _CleanBruteForceRecords cleanBruteForceRecords;
    private final _UpdateBruteForceRecords updateBruteForceRecords;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final _MfaChain mfaChain;
    private final PendingAuthenticationStore pendingStore;

    Authentication(_BruteForceGuard bruteForceGuard,
                   _VerifyCredentials verifyCredentials,
                   _RequireVerifiedEmail requireVerifiedEmail,
                   _GenerateSession generateSession,
                   _CleanBruteForceRecords cleanBruteForceRecords,
                   _UpdateBruteForceRecords updateBruteForceRecords,
                   EnrolledFactorRepository enrolledFactorRepository,
                   _MfaChain mfaChain,
                   PendingAuthenticationStore pendingStore) {
        this.bruteForceGuard = bruteForceGuard;
        this.verifyCredentials = verifyCredentials;
        this.requireVerifiedEmail = requireVerifiedEmail;
        this.generateSession = generateSession;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.updateBruteForceRecords = updateBruteForceRecords;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.mfaChain = mfaChain;
        this.pendingStore = pendingStore;
    }

    public AuthenticationResult execute(AuthenticationRequest request) {
        Source source = request.source();
        Credentials credentials = new Credentials(request.email(), request.plaintextPassword());

        return switch (bruteForceGuard.execute(source)) {
            case BruteForceProtectionEvent.Blocked blocked -> new AuthenticationResult.Blocked(blocked.authenticationBlock());
            case BruteForceProtectionEvent.Allowed _ -> switch (verifyCredentials.execute(credentials)) {
                case AuthenticationEvent.Valid valid -> {
                    // correct credentials are not a guessing signal, so no brute-force update here
                    if (!requireVerifiedEmail.isVerified(valid.email())) {
                        yield new AuthenticationResult.EmailNotVerified();
                    }
                    cleanBruteForceRecords.execute(source);
                    // link #1 passed. With enrolled factors the session waits until the chain
                    // completes; with none it is minted now (unchanged single-factor sign-in).
                    List<EnrolledFactor> factors = enrolledFactorRepository.findByUser(valid.email());
                    if (factors.isEmpty()) {
                        yield new AuthenticationResult.Authenticated(generateSession.create(valid.email()));
                    }
                    String ticket = pendingStore.open(mfaChain.begin(valid.email(), factors));
                    yield new AuthenticationResult.MfaRequired(ticket, factors.get(0).type());
                }
                case AuthenticationEvent.Invalid _ -> {
                    updateBruteForceRecords.execute(source);
                    yield new AuthenticationResult.Rejected();
                }
            };
        };
    }
}
