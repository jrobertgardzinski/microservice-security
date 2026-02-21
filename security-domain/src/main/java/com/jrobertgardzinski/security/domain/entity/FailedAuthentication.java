package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationId;

public record FailedAuthentication(
        FailedAuthenticationDetails details,
        FailedAuthenticationId id) {
}
