package com.jrobertgardzinski.security.system.account;

/**
 * Outcome of {@link RequestEmailChange}: a verification link was sent to the new address, or that
 * address is already taken.
 */
public sealed interface RequestEmailChangeResult {

    record Requested() implements RequestEmailChangeResult {}

    record EmailTaken() implements RequestEmailChangeResult {}
}
