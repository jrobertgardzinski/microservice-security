package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;

/**
 * A factor a user has registered, and where to reach it. {@code secretMaterial} is factor-specific
 * and protected per factor (an e-mail/phone target for the code channels; an encrypted secret for
 * TOTP; hashed for recovery codes). {@code order} fixes the position in the sign-in chain; the
 * password / OAuth login is always link #1 and is not one of these rows.
 */
public record EnrolledFactor(Email userEmail, FactorType type, String label, int order, String secretMaterial) {
}
