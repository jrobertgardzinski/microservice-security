package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationId;

/**
 * A recorded instance of a rejected authentication attempt.
 */
public record RejectedAuthentication(
        RejectedAuthenticationDetails details,
        RejectedAuthenticationId id) {
}
