package com.jrobertgardzinski.hash.algorithm.argon2;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.factory.PasswordFactory;
import com.jrobertgardzinski.password.policy.PasswordPolicyAdapter;
import com.jrobertgardzinski.salt.domain.Salt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class HashAlgorithmPortTest {

    protected abstract HashAlgorithmPort hashAlgorithm();

    private final PasswordFactory passwordFactory = new PasswordFactory(new PasswordPolicyAdapter());
    private PasswordHash hash;

    @BeforeEach
    void init() {
        PlaintextPassword password = passwordFactory.create("StrongPassword1!");
        hash = hashAlgorithm().hash(password, new Salt("salt123456"));
    }

    @Test
    void correctPasswordVerifies() {
        assertTrue(hashAlgorithm().verify(hash, passwordFactory.create("StrongPassword1!")));
    }

    @Test
    void wrongPasswordDoesNotVerify() {
        assertFalse(hashAlgorithm().verify(hash, passwordFactory.create("StrongPassword2!")));
    }
}
