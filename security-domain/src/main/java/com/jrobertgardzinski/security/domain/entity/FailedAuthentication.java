package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationId;

/**
 * A recorded instance of a failed authentication attempt.
 */
public record FailedAuthentication(
        FailedAuthenticationDetails details,
        FailedAuthenticationId id) {
}
