package com.jrobertgardzinski.hash.algorithm.argon2;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.salt.domain.Salt;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public class Argon2HashAlgorithm implements HashAlgorithmPort {

    static final int ITERATIONS = 20;
    static final int MEM_LIMIT = 66536;
    static final int PARALLELISM = 1;
    static final Argon2 argon2 = Argon2Factory.create();

    @Override
    public PasswordHash hash(PlaintextPassword plaintextPassword, Salt salt) {
        String hash = argon2.hash(ITERATIONS, MEM_LIMIT, PARALLELISM, plaintextPassword.value().getBytes());
        return new PasswordHash(hash);
    }

    @Override
    public boolean verify(PasswordHash passwordHash, PlaintextPassword plaintextPassword) {
        return argon2.verify(
                passwordHash.value(),
                plaintextPassword.value().getBytes()
        );
    }
}
