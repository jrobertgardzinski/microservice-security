package com.jrobertgardzinski.security.domain.vo;

/**
 * What a lockout is counted against: a {@link Source} knocking on an {@link AttemptedAccount}.
 *
 * <p>Both halves are needed, and the reason is a pair of failures the estate has already lived
 * through. Counting by SOURCE alone made a successful sign-in able to wipe the whole address's
 * record — one known-good credential, an attacker's own account, became a RESET button for every
 * account behind that address. Removing that reset left the other edge of the same knife: three
 * mistyped passwords from one office, one CGNAT or one CI runner lock out everybody behind it. The
 * project's own e2e suite blocked ITSELF halfway through a run before this type existed, which is
 * as clear a demonstration as anyone could ask for.
 *
 * <p>Counting by ACCOUNT alone would be worse in the other direction: anyone could lock a victim
 * out on demand from anywhere. So the pair is the unit — an attacker's failures are charged to
 * their own source and to the account they are actually attacking, and a success clears only that
 * pair. The source-wide ceiling above it (a much higher one) is what still catches a sprayer
 * walking across many accounts.
 *
 * <p>Equality lives HERE, in one place, so the JDBC adapter and its in-memory twin cannot drift
 * apart on what "the same subject" means — the drift that made green tests prove the wrong thing
 * once already.
 */
public record LockoutSubject(Source source, AttemptedAccount account) {

    public LockoutSubject {
        if (source == null || account == null) {
            throw new IllegalArgumentException("a lockout subject is a source AND an account");
        }
    }
}
