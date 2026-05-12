package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

/**
 * Fast, deterministic stand-in for a real hash algorithm. Production code uses
 * Argon2; system-level tests care about orchestration, not hashing cost.
 */
final class StubHashAlgorithm implements HashAlgorithmPort {

    @Override
    public HashedPassword hash(PlaintextPassword plaintextPassword) {
        return new HashedPassword("hash:" + plaintextPassword.value());
    }

    @Override
    public boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword) {
        return hashedPassword.value().equals("hash:" + plaintextPassword.value());
    }
}
