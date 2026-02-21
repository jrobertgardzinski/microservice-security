package com.jrobertgardzinski.hash.algorithm.argon2;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;

class Argon2HashAlgorithmTest extends HashAlgorithmPortTest {

    @Override
    protected HashAlgorithmPort hashAlgorithm() {
        return new Argon2HashAlgorithm();
    }
}
