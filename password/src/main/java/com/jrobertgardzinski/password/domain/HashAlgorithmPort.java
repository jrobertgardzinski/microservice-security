package com.jrobertgardzinski.password.domain;

import com.jrobertgardzinski.salt.domain.Salt;

public interface HashAlgorithmPort {
    PasswordHash hash(PlaintextPassword plaintextPassword, Salt salt);
    boolean verify(PasswordHash passwordHash, PlaintextPassword plaintextPassword);
}
