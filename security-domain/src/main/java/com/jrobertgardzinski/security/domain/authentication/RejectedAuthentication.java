package com.jrobertgardzinski.security.domain.authentication;


/**
 * A recorded instance of a rejected authentication attempt.
 */
public record RejectedAuthentication(
        RejectedAuthenticationDetails details,
        RejectedAuthenticationId id) {
}
