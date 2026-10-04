package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.security.domain.vo.Source;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * A temporary suspension of authentication attempts from a given {@link Source}. The block is
 * keyed by the source's identity (its IP address) — see {@link Source} for why the observed
 * context stays out of the key.
 */
public record AuthenticationBlock (
        Source source,
        LocalDateTime expiryDate
) {
    public boolean isStillActive(Clock clock) {
        return expiryDate.isAfter(LocalDateTime.now(clock));
    }
}
