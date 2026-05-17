package com.jrobertgardzinski.security.domain.vo;

/**
 * Identity factor used during multi-factor authentication.
 * Extensible — new factors get a new variant and a matching use case in security-system.
 */
public enum FactorType {
    CREDENTIALS,
    EMAIL_OTP
}
