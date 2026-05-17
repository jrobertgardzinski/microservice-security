package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.MfaPolicyPort;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.List;

public final class StubMfaPolicy implements MfaPolicyPort {

    private final List<FactorType> factorsForEveryone;

    public StubMfaPolicy(FactorType... factorsForEveryone) {
        this.factorsForEveryone = List.of(factorsForEveryone);
    }

    @Override
    public List<FactorType> requiredFactors(Email email) {
        return factorsForEveryone;
    }
}
