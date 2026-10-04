package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;

/**
 * A factor a user has registered, and where to reach it. {@code secretMaterial} is factor-specific,
 * and what protects it is the ADAPTER's business: an e-mail or phone target for the code channels
 * (not a secret — it is where the code is sent), a public key for a passkey, and for TOTP the seed,
 * which the JDBC adapter encrypts at rest because that one MINTS codes rather than receiving them. {@code order} fixes the position in the sign-in chain; the
 * password / OAuth login is always link #1 and is not one of these rows.
 */
public record EnrolledFactor(Email userEmail, FactorType type, String label, int order, String secretMaterial) {
}
