package com.jrobertgardzinski.email.usecases.isemployee;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

import java.util.Set;

public class IsEmployee implements EmailPolicy {

    private final Set<String> companyDomains;

    public IsEmployee(Set<String> companyDomains) {
        this.companyDomains = Set.copyOf(companyDomains);
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return companyDomains.contains(email.domain().value());
    }
}
