package com.jrobertgardzinski.email.domain;

/**
 * Single abstraction for both email validation rules and system decisions.
 * Specs (RFC, Gmail, disposable) and use cases (CanRegister, IsEmployee)
 * all implement this interface — they are all policies at different levels.
 */
public interface EmailPolicy {

    boolean isSatisfiedBy(Email email);

    default EmailPolicy and(EmailPolicy other) {
        return email -> this.isSatisfiedBy(email) && other.isSatisfiedBy(email);
    }

    default EmailPolicy or(EmailPolicy other) {
        return email -> this.isSatisfiedBy(email) || other.isSatisfiedBy(email);
    }

    default EmailPolicy negate() {
        return email -> !this.isSatisfiedBy(email);
    }
}
