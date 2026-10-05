package com.jrobertgardzinski.security.domain.session;

import com.jrobertgardzinski.email.domain.Email;

/**
 * What a valid access token grants: whose session it is and when it expires. Found from a presented
 * access token (matched by its hash); the raw token is never stored, so it is absent here.
 */
public record AccessGrant(Email email, AccessTokenExpiration expiration) {
}
