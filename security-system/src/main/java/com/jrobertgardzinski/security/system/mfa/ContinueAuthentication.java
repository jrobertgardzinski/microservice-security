package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.core.FailedSignIns;
import com.jrobertgardzinski.security.domain.core.Sessions;
import com.jrobertgardzinski.security.domain.core.UserRepository;
import com.jrobertgardzinski.security.domain.core.VerifiedAddresses;
import com.jrobertgardzinski.security.domain.mfa.EnrolledFactor;
import com.jrobertgardzinski.security.domain.mfa.PendingAuthentication;
import com.jrobertgardzinski.security.domain.mfa.PendingAuthenticationStore;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Drives a sign-in that is past link #1 through its remaining factors, one proof at a time. Lives
 * in the authentication package so it can mint the session through the same {@link _GenerateSession}
 * the password path uses — the session is identical however the chain was walked. A wrong proof
 * costs an attempt AND is written down against the same pair a wrong password is — this address
 * against this account, which is why the chain remembers where it began;
 * running out, or an unknown/expired ticket, ends the attempt.
 */
public class ContinueAuthentication {

    private final PendingAuthenticationStore store;
    private final MfaChain chain;
    private final Sessions sessions;
    private final UserRepository users;
    private final VerifiedAddresses verifiedAddresses;
    private final FailedSignIns failedSignIns;
    private final Clock clock;

    public ContinueAuthentication(PendingAuthenticationStore store, MfaChain chain, Sessions sessions,
                                  UserRepository users, VerifiedAddresses verifiedAddresses,
                                  FailedSignIns failedSignIns, Clock clock) {
        this.store = store;
        this.chain = chain;
        this.sessions = sessions;
        this.users = users;
        this.verifiedAddresses = verifiedAddresses;
        this.failedSignIns = failedSignIns;
        this.clock = clock;
    }

    public ContinueAuthenticationResult execute(String ticket, String proof) {
        Optional<PendingAuthentication> found = store.find(ticket);
        if (found.isEmpty() || found.get().isExpired(clock)) {
            store.close(ticket);
            return new ContinueAuthenticationResult.InvalidTicket();
        }
        PendingAuthentication pending = found.get();

        if (!chain.verify(pending, proof)) {
            // A wrong proof is a wrong guess at this account from this address, so it is written
            // down like a wrong password. Five-per-ticket was never a limit on the person: link #1
            // is a password they already hold, so a new ticket costs one request and the codes
            // could be walked at whatever rate tickets could be minted.
            pending.lockoutSubject().ifPresent(failedSignIns::record);
            // spend the attempt as one step and decide on what is NOW stored: reading the count,
            // subtracting one and writing it back as three calls let concurrent proofs share the
            // same attempt, so a ticket worth five guesses answered as many as were sent at once
            Optional<PendingAuthentication> afterWrong = store.update(ticket, PendingAuthentication::afterWrongProof);
            if (afterWrong.isEmpty() || afterWrong.get().attemptsLeft() <= 0) {
                store.close(ticket);
                return new ContinueAuthenticationResult.TooManyAttempts();
            }
            return new ContinueAuthenticationResult.WrongProof(afterWrong.get().attemptsLeft());
        }

        List<EnrolledFactor> tail = pending.tail();
        if (tail.isEmpty()) {
            store.close(ticket);
            // the chain proved the PERSON; whether the ACCOUNT still signs in is a separate question,
            // and link #1's answer to it is as old as the chain took to walk. A deletion requested
            // after the password step used to be answered with a brand-new session.
            if (!stillSignsIn(pending.email())) {
                return new ContinueAuthenticationResult.InvalidTicket();
            }
            return new ContinueAuthenticationResult.Completed(sessions.open(pending.email()));
        }
        PendingAuthentication advanced = chain.advanceTo(pending, tail);
        store.replace(ticket, advanced);
        return new ContinueAuthenticationResult.NextFactor(tail.get(0).type(), advanced.challengeData());
    }

    /**
     * The account may have changed while the chain was in flight: deleted, closing, or its address
     * no longer verified. A completed chain for an account that would not be let in by password
     * must not be let in either.
     */
    private boolean stillSignsIn(Email email) {
        return users.findBy(email).isPresent()
                && !users.isPendingDeletion(email)
                && verifiedAddresses.isVerified(email);
    }
}
