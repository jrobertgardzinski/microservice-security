package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.core.FailedSignIns;
import com.jrobertgardzinski.security.domain.core.SecondFactors;
import com.jrobertgardzinski.security.domain.core.Sessions;
import com.jrobertgardzinski.security.domain.core.VerifiedAddresses;
import java.util.Optional;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.authentication.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.authentication.Credentials;
import com.jrobertgardzinski.security.domain.core.AttemptedAccount;
import com.jrobertgardzinski.security.domain.core.LockoutSubject;
import com.jrobertgardzinski.security.domain.core.Source;


public class Authentication {
    private final _BruteForceGuard bruteForceGuard;
    private final _VerifyCredentials verifyCredentials;
    private final VerifiedAddresses verifiedAddresses;
    private final Sessions sessions;
    private final _CleanBruteForceRecords cleanBruteForceRecords;
    private final FailedSignIns failedSignIns;
    private final SecondFactors secondFactors;

    Authentication(_BruteForceGuard bruteForceGuard,
                   _VerifyCredentials verifyCredentials,
                   VerifiedAddresses verifiedAddresses,
                   Sessions sessions,
                   _CleanBruteForceRecords cleanBruteForceRecords,
                   FailedSignIns failedSignIns,
                   SecondFactors secondFactors) {
        this.bruteForceGuard = bruteForceGuard;
        this.verifyCredentials = verifyCredentials;
        this.verifiedAddresses = verifiedAddresses;
        this.sessions = sessions;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.failedSignIns = failedSignIns;
        this.secondFactors = secondFactors;
    }

    public AuthenticationResult execute(AuthenticationRequest request) {
        Source source = request.source();
        Credentials credentials = new Credentials(request.email(), request.plaintextPassword());
        // failures are charged to the PAIR: this address against this account. Either half alone is
        // walkable-around — by account, anyone locks out a victim from anywhere; by address, three
        // typos take a whole office down with them.
        LockoutSubject subject = new LockoutSubject(source, AttemptedAccount.of(request.email()));

        return switch (bruteForceGuard.execute(subject)) {
            case BruteForceProtectionEvent.Blocked blocked -> new AuthenticationResult.Blocked(blocked.authenticationBlock());
            case BruteForceProtectionEvent.Allowed _ -> switch (verifyCredentials.execute(credentials)) {
                case AuthenticationEvent.Valid valid -> {
                    // correct credentials are not a guessing signal, so no brute-force update here
                    if (!verifiedAddresses.isVerified(valid.email())) {
                        yield new AuthenticationResult.EmailNotVerified();
                    }
                    // The person got their own password right, so THEIR earlier misses stop counting
                    // — this pair's and nobody else's. Clearing the whole address (what this used to
                    // do) made one known-good credential an amnesty for everything that address was
                    // trying; clearing nothing at all (what it did between P18 poz. 6 and now) left
                    // an office locked out over one person's typos. A placed BLOCK is untouched
                    // either way: it expires on its own timer.
                    cleanBruteForceRecords.execute(subject);
                    // link #1 passed. With enrolled factors the session waits until the chain
                    // completes; with none it is minted now (unchanged single-factor sign-in).
                    yield secondFactors.challenge(valid.email(), Optional.of(source))
                            .<AuthenticationResult>map(chain -> new AuthenticationResult.MfaRequired(
                                    chain.ticket(), chain.factor(), chain.challengeData()))
                            .orElseGet(() -> new AuthenticationResult.Authenticated(sessions.open(valid.email())));
                }
                case AuthenticationEvent.Invalid _ -> {
                    failedSignIns.record(subject);
                    yield new AuthenticationResult.Rejected();
                }
            };
        };
    }
}
