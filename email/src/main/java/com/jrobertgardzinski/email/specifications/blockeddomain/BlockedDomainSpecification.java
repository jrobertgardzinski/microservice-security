package com.jrobertgardzinski.email.specifications.blockeddomain;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

import java.util.Set;

public class BlockedDomainSpecification implements EmailPolicy {

    private final Set<String> blockedDomains;

    public BlockedDomainSpecification(Set<String> blockedDomains) {
        this.blockedDomains = Set.copyOf(blockedDomains);
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return !blockedDomains.contains(email.domain().value());
    }
}
