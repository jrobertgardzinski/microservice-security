package com.jrobertgardzinski.email.domain;

/**
 * Creates Email value objects while enforcing the active policy.
 * Single entry point for producing Email instances in application code.
 */
public class EmailFactory {

    private final EmailPolicy policy;

    public EmailFactory(EmailPolicy policy) {
        this.policy = policy;
    }

    public Email create(String rawValue) {
        Email candidate = Email.of(rawValue);
        if (!policy.isSatisfiedBy(candidate)) {
            throw new InvalidEmailException(rawValue, policy);
        }
        return candidate;
    }
}
