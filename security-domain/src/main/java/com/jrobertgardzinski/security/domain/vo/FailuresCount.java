package com.jrobertgardzinski.security.domain.vo;

/**
 * Consecutive failed authentication count for a given {@link IpAddress}.
 */
public record FailuresCount(int count) {
    public boolean hasReachedTheLimit(int limit) {
        return count >= limit;
    }
}
