package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The password half of one registration attempt: the outcome (a hash, when the policy let the
 * plaintext through) and the policy it was measured against, kept together so a refusal can name
 * the minimum length or the special characters that were in force.
 */
record _PasswordVerdict(Outcome<HashedPassword> outcome, PasswordPolicy policy) {

    /** Judges the candidate against the policy in force and hashes it if it passes. */
    static _PasswordVerdict judge(HashAlgorithmPort hashAlgorithm, PasswordPolicy policy,
                                  Supplier<PlaintextPassword> candidate) {
        return new _PasswordVerdict(new CreatePasswordHash(hashAlgorithm, policy).create(candidate), policy);
    }

    Optional<HashedPassword> accepted() {
        return outcome.findValue();
    }

    PasswordErrorCodes errorCodes() {
        return PasswordErrorCodes.of(outcome);
    }
}
