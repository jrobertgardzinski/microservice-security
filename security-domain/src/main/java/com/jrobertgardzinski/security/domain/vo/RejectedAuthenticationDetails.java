package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;

import java.time.LocalDateTime;

/**
 * Details of a {@link RejectedAuthentication}. Carries the whole {@link Source} — the identity
 * counts towards the lockout, the observed context stays for forensics.
 */
public record RejectedAuthenticationDetails(Source source, LocalDateTime time) {
}
