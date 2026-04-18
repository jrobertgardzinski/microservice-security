package com.jrobertgardzinski.security.domain.vo;

import java.time.LocalDateTime;

/**
 * Details of a {@link com.jrobertgardzinski.security.domain.entity.FailedAuthentication}.
 */
public record FailedAuthenticationDetails(IpAddress ipAddress, LocalDateTime time) {
}
