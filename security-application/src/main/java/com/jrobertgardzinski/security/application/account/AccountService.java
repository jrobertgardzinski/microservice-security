package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.account.ChangePasswordResult;
import com.jrobertgardzinski.security.system.core.SourceThrottle;

/** What a signed-in user changes about their own account that no mail has to prove: the password. */
public final class AccountService {

    private final ChangePassword changePassword;
    private final SourceThrottle changePasswordThrottle;
    private final TransactionBoundary transactionBoundary;

    public AccountService(ChangePassword changePassword, SourceThrottle changePasswordThrottle,
                          TransactionBoundary transactionBoundary) {
        this.changePassword = changePassword;
        this.changePasswordThrottle = changePasswordThrottle;
        this.transactionBoundary = transactionBoundary;
    }

    public PasswordChange changePassword(Email caller, String currentPassword, String newPassword, IpAddress source) {
        // changing a password verifies the CURRENT one with a full hash: unthrottled it is a password
        // oracle for whoever holds a live (possibly stolen) access token
        SourceThrottle.Decision decision = changePasswordThrottle.check(source);
        if (!decision.allowed()) {
            return new PasswordChange.Throttled(decision.retryAfterSeconds());
        }
        if (blank(currentPassword) || blank(newPassword)) {
            return new PasswordChange.Incomplete();
        }
        return switch (transactionBoundary.execute(() -> changePassword.execute(
                caller, () -> PlaintextPassword.of(currentPassword), () -> PlaintextPassword.of(newPassword)))) {
            case ChangePasswordResult.Changed changed -> new PasswordChange.Changed();
            case ChangePasswordResult.WrongCurrentPassword wrong -> new PasswordChange.WrongCurrentPassword();
            case ChangePasswordResult.WeakPassword weak -> new PasswordChange.WeakPassword();
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public sealed interface PasswordChange {

        record Changed() implements PasswordChange {}

        record WrongCurrentPassword() implements PasswordChange {}

        record WeakPassword() implements PasswordChange {}

        /** One of the two passwords is missing or blank. */
        record Incomplete() implements PasswordChange {}

        record Throttled(long retryAfterSeconds) implements PasswordChange {}
    }
}
