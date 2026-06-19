package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;

import java.time.LocalDateTime;

/**
 * Details of a {@link RejectedAuthentication}.
 */
public record RejectedAuthenticationDetails(IpAddress ipAddress, LocalDateTime time) {
}
