package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.security.domain.vo.Email;

public record User(
        Email email,
        PasswordHash passwordHash
) {
}
