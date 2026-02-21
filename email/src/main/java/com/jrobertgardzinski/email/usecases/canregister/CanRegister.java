package com.jrobertgardzinski.email.usecases.canregister;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;
import com.jrobertgardzinski.email.specifications.blockeddomain.BlockedDomainSpecification;
import com.jrobertgardzinski.email.specifications.disposable.DisposableEmailSpecification;
import com.jrobertgardzinski.email.specifications.rfc.RfcFormatSpecification;

import java.util.Set;

public class CanRegister implements EmailPolicy {

    private final EmailPolicy policy;

    public CanRegister(Set<String> disposableDomains, Set<String> blockedDomains) {
        this.policy = new RfcFormatSpecification()
                .and(new DisposableEmailSpecification(disposableDomains))
                .and(new BlockedDomainSpecification(blockedDomains));
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return policy.isSatisfiedBy(email);
    }
}
