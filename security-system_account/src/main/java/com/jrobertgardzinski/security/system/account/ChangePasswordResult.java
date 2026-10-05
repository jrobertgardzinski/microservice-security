package com.jrobertgardzinski.security.system.account;

/**
 * Outcome of {@link ChangePassword}: the password was changed, the current password was wrong, or
 * the new password did not meet the policy.
 */
public sealed interface ChangePasswordResult {

    record Changed() implements ChangePasswordResult {}

    record WrongCurrentPassword() implements ChangePasswordResult {}

    record WeakPassword() implements ChangePasswordResult {}
}
