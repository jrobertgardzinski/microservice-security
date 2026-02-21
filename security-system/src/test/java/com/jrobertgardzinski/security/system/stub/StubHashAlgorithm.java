package com.jrobertgardzinski.security.system.stub;

import com.jrobertgardzinski.hash.algorithm.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.salt.domain.Salt;

public class StubHashAlgorithm implements HashAlgorithmPort {
    @Override
    public PasswordHash hash(PlaintextPassword plaintextPassword, Salt salt) {
        return new PasswordHash(
                salt.value() + plaintextPassword.value()
        );
    }

    @Override
    public boolean verify(PasswordHash passwordHash, PlaintextPassword plaintextPassword) {
        return passwordHash.value().endsWith(plaintextPassword.value());
    }
}
