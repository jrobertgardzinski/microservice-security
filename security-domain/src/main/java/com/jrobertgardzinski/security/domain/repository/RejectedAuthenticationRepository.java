package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;

import java.time.LocalDateTime;

/**
 * The failed-attempt log the brute-force guard counts over a time window — on TWO scales, because
 * one scale can always be walked around:
 *
 * <ul>
 *   <li>per {@link LockoutSubject} — this source against THIS account. Tight limit: it is the
 *       shape of someone guessing one person's password.</li>
 *   <li>per {@link Source} — this address against anything at all. A far higher ceiling: it is the
 *       shape of someone spraying two or three guesses across a thousand accounts, which the
 *       per-subject count alone would never notice.</li>
 * </ul>
 *
 * <p>How the account is STORED is the adapter's business, and is deliberately not described here —
 * not even in passing. What matters at this level is only that two attempts aimed at the same
 * account count as the same account; whether the database holds the address itself or something
 * derived from it is a decision that belongs one layer down, together with anything it needs to
 * keep secret. Writing the question down here would be the first step to answering it here.
 */
public interface RejectedAuthenticationRepository {

    RejectedAuthentication create(RejectedAuthenticationDetails value);

    /** Failures of this source against this account — the tight, per-victim count. */
    FailuresCount countFailuresOnAccount(LockoutSubject subject, LocalDateTime since);

    /** Failures of this source against anything — the ceiling that catches spraying. */
    FailuresCount countFailuresFromSource(Source source, LocalDateTime since);

    /**
     * Forget this pair's failures — and ONLY this pair's.
     *
     * <p>Wiping everything a source ever did is what made a successful sign-in a reset button for
     * an entire address; narrowing it is what keeps the escape valve for a person who mistyped
     * their own password twice without handing that valve to an attacker with an account of their
     * own.
     */
    void removeAllFor(LockoutSubject subject);
}
