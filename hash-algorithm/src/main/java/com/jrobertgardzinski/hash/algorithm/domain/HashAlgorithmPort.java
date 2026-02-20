package com.jrobertgardzinski.hash.algorithm.domain;

import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.salt.domain.Salt;

public interface HashAlgorithmPort {
    PasswordHash hash(PlaintextPassword plaintextPassword, Salt salt);
    boolean verify(PasswordHash passwordHash, PlaintextPassword plaintextPassword);
}
