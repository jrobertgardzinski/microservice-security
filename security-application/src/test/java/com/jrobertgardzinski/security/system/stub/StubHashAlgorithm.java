package com.jrobertgardzinski.security.system.stub;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

public class StubHashAlgorithm implements HashAlgorithmPort {
    @Override
    public HashedPassword hash(PlaintextPassword plaintextPassword) {
        return new HashedPassword(plaintextPassword.value());
    }

    @Override
    public boolean verify(HashedPassword passwordHash, PlaintextPassword plaintextPassword) {
        return passwordHash.value().endsWith(plaintextPassword.value());
    }
}
