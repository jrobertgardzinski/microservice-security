package com.jrobertgardzinski.email.usecases.istrusteduser;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

import java.util.Set;

public class IsTrustedUser implements EmailPolicy {

    private final Set<String> whitelistedAddresses;
    private final Set<String> trustedDomains;

    public IsTrustedUser(Set<String> whitelistedAddresses, Set<String> trustedDomains) {
        this.whitelistedAddresses = Set.copyOf(whitelistedAddresses);
        this.trustedDomains       = Set.copyOf(trustedDomains);
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return whitelistedAddresses.contains(email.value())
                || trustedDomains.contains(email.domain().value());
    }
}
