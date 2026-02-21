package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.password.domain.PlaintextPassword;

public record AuthenticationRequest (
        IpAddress ipAddress,
        Email email,
        PlaintextPassword plaintextPassword
) {
}