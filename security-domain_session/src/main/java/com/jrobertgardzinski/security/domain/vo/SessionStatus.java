package com.jrobertgardzinski.security.domain.vo;

/**
 * Lifecycle of a stored session. A session is {@code ACTIVE} until it is refreshed, when it becomes
 * {@code ROTATED} — kept (not deleted) so that replaying its now-superseded refresh token is
 * recognised as token theft.
 */
public enum SessionStatus {
    ACTIVE,
    ROTATED
}
