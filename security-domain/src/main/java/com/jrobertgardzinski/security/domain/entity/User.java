package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.vo.Email;

public record User(
        Email email,
        HashedPassword passwordHash
) {
}
