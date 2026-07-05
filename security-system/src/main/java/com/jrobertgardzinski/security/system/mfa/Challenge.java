package com.jrobertgardzinski.security.system.mfa;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * What the boundary remembers between issuing a challenge and verifying the proof: the hashed
 * secret (never the raw code) and when it expires. Single-use and attempt-limited are the chain's
 * concern (they live on the pending authentication), not the challenge's.
 */
public record Challenge(String codeHash, LocalDateTime expiresAt) {

    public boolean isExpired(Clock clock) {
        return expiresAt.isBefore(LocalDateTime.now(clock));
    }
}
