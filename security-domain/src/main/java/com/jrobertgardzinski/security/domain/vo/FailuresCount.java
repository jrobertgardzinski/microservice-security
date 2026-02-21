package com.jrobertgardzinski.security.domain.vo;

public record FailuresCount(int count) {
    public boolean hasReachedTheLimit(int limit) {
        return count >= limit;
    }
}
