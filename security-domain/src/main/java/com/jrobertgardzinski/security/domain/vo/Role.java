package com.jrobertgardzinski.security.domain.vo;

/**
 * What a user is allowed to be across the whole system (flat RBAC): every signed-in user is a
 * {@link #USER}; a {@link #MODERATOR} may act on other people's content (hide, remove, flag); an
 * {@link #ADMIN} additionally administers the system, including granting these very roles. Roles
 * are global authority — ownership ("my meme") and per-server membership are separate mechanisms.
 */
public enum Role {
    USER,
    MODERATOR,
    ADMIN
}
