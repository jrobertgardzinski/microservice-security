package com.jrobertgardzinski.security.domain.validation;

import java.util.List;

public final class ConfigurablePasswordPolicyAdapter2 implements PasswordPolicyPort2 {

    private final List<PasswordSpecification2> specifications;

    public ConfigurablePasswordPolicyAdapter2(List<PasswordSpecification2> specifications) {
        this.specifications = specifications;
    }

    @Override
    public List<PasswordSpecification2> specifications() {
        return specifications;
    }
}
