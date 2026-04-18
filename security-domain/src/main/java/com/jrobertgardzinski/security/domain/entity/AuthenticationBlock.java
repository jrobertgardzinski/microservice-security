package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * A temporary suspension of authentication attempts from a given {@link IpAddress}.
 */
public record AuthenticationBlock (
        IpAddress ipAddress,
        LocalDateTime expiryDate
) {
    public boolean isStillActive(Clock clock) {
        return expiryDate.isAfter(LocalDateTime.now(clock));
    }
}
