package com.jrobertgardzinski.security.domain.repository;

/**
 * Thrown by {@link UserRepository#save} when persistence rejects a user because the (normalized)
 * email is already taken — the storage-level uniqueness guarantee, which closes the check-then-act
 * race that a prior {@code existsBy} check alone cannot.
 */
public class EmailAlreadyTakenException extends RuntimeException {
    public EmailAlreadyTakenException() {
        super("email already taken");
    }
}
