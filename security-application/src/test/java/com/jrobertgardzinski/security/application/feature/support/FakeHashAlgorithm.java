package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

/**
 * Test double for {@link HashAlgorithmPort}: stores the plaintext verbatim and compares by equality.
 * Hashing is not under test in the authentication scenarios; this keeps them fast.
 */
public final class FakeHashAlgorithm implements HashAlgorithmPort {

    @Override
    public HashedPassword hash(PlaintextPassword plaintextPassword) {
        return new HashedPassword(plaintextPassword.value());
    }

    @Override
    public boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword) {
        return hashedPassword.value().equals(plaintextPassword.value());
    }
}
