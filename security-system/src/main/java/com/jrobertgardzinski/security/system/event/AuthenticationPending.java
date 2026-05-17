package com.jrobertgardzinski.security.system.event;

import com.jrobertgardzinski.security.domain.vo.AuthSessionId;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.time.LocalDateTime;

/**
 * Returned when authentication needs another factor to complete.
 * The client carries {@code authSessionId} forward when calling the next factor's endpoint.
 */
public record AuthenticationPending(
        AuthSessionId authSessionId,
        FactorType nextFactor,
        LocalDateTime expiresAt) implements AuthenticationResult {
}
