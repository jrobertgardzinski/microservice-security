package com.jrobertgardzinski.hash.algorithm.argon2;

import com.jrobertgardzinski.hash.algorithm.domain.HashAlgorithmPort;
import com.jrobertgardzinski.hash.algorithm.domain.HashAlgorithmPortTest;

class Argon2HashAlgorithmTest extends HashAlgorithmPortTest {

    @Override
    protected HashAlgorithmPort hashAlgorithm() {
        return new Argon2HashAlgorithm();
    }
}
