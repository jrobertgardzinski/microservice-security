package com.jrobertgardzinski.security.system.settings;

import com.jrobertgardzinski.password.policy.PasswordPolicy;

/**
 * The read side of the password settings: what the policy IS right now, for the attempt about to
 * be judged. The mirror of {@link MinPasswordLengthStore}, which is the write side — and
 * deliberately a separate port, because the two answer different questions: the store lays a value
 * on one rung, while this reports the winner of the whole ladder. Write 3 through the store and
 * this may still answer 5, because the ladder refused the row and fell through; on one interface
 * that would read as a bug rather than as the design.
 *
 * A use case holds this port, not a policy: it outlives any single value, so it must ask per
 * attempt — an administrator's decision has to be honoured by the next request, without a restart.
 * What stands behind it (a database rung over a property over a compiled-in default, a cache, a
 * refused row) belongs to the adapter; a use case only ever learns the answer.
 */
@FunctionalInterface
public interface PasswordPolicyInForce {

    /** The policy in force at this moment; never null. */
    PasswordPolicy current();
}
