package com.jrobertgardzinski.email.usecases.canresetpassword;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

import java.util.List;

public class CanResetPassword implements EmailPolicy {

    private final List<EmailPolicy> policies;

    public CanResetPassword(List<EmailPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return policies.stream().allMatch(p -> p.isSatisfiedBy(email));
    }
}
