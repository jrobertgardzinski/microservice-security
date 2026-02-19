package com.jrobertgardzinski.email.specifications.disposable;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

import java.util.Set;

public class DisposableEmailSpecification implements EmailPolicy {

    private final Set<String> disposableDomains;

    public DisposableEmailSpecification(Set<String> disposableDomains) {
        this.disposableDomains = Set.copyOf(disposableDomains);
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return !disposableDomains.contains(email.domain().value());
    }
}
