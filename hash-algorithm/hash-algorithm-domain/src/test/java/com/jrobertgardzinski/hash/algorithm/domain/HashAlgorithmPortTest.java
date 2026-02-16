package com.jrobertgardzinski.hash.algorithm.domain;

import com.jrobertgardzinski.security.domain.factory.PlaintextPasswordFactory;
import com.jrobertgardzinski.security.domain.validation.password.ConfigurablePasswordPolicyAdapter;
import com.jrobertgardzinski.security.domain.vo.PasswordHash;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.Salt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class HashAlgorithmPortTest {

    protected abstract HashAlgorithmPort hashAlgorithm();
    PlaintextPasswordFactory plaintextPasswordFactory = new PlaintextPasswordFactory(new ConfigurablePasswordPolicyAdapter());
    PasswordHash hash;

    @BeforeEach
    void init() {
        hash = hashAlgorithm().hash(plaintextPasswordFactory.create("StrongPassword1!"), new Salt("salt123456"));
    }

    @Test
    void negative() {
        assertFalse(hashAlgorithm().verify(hash, plaintextPasswordFactory.create("StrongPassword2!")));
    }

    @Test
    void positive() {
        assertTrue(hashAlgorithm().verify(hash, plaintextPasswordFactory.create("StrongPassword1!")));
    }
}