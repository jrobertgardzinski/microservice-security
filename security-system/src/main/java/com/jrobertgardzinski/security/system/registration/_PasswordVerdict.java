package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.Optional;
import java.util.function.Supplier;

record _PasswordVerdict(Outcome<HashedPassword> outcome, PasswordPolicy policy) {

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
