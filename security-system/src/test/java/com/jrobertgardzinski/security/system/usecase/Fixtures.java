package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.util.constraint.Constraints;

import java.util.List;

/**
 * Sample inputs and the password constraint set used by both the production
 * wiring and the assertions. Keeping them in one place is what the feature
 * file's description means by "single source of truth": when the configured
 * constraints change, valid/invalid samples follow.
 */
public final class Fixtures {

    public static final String VALID_EMAIL = "valid.user@example.com";
    public static final String INVALID_EMAIL = "not-an-email";

    public static final String VALID_PASSWORD = "StrongPassword1#";
    public static final String INVALID_PASSWORD = "x";

    public static final String ANOTHER_VALID_PASSWORD = "AnotherStrong2$";

    public static Constraints<PlaintextPassword> passwordConstraints() {
        return new Constraints<>(List.of(new MinLengthAtLeast(8)));
    }

    private Fixtures() {}
}
