package com.jrobertgardzinski.security.domain.core;

/**
 * Failed sign-in attempts, as the brute-force guard counts them. A wrong password is one; so is a
 * wrong proof at a factor of a sign-in that began at the same address.
 */
public interface FailedSignIns {

    void record(LockoutSubject subject);
}
