package com.jrobertgardzinski.security.domain.validation;

import java.util.ArrayList;
import java.util.List;

public final class ConfigurablePasswordPolicyAdapter2 implements PasswordPolicyPort2 {

    private final List<PasswordSpecification2> specifications;

    public ConfigurablePasswordPolicyAdapter2() {
        this(PasswordPolicyConfig.builder().build());
    }

    public ConfigurablePasswordPolicyAdapter2(PasswordPolicyConfig config) {
        List<PasswordSpecification2> specs = new ArrayList<>();
        specs.add(new MinLengthSpecification(config.minLength()));
        if (config.requireLowercase()) specs.add(new ContainsLowercaseSpecification());
        if (config.requireUppercase()) specs.add(new ContainsUppercaseSpecification());
        if (config.requireDigit()) specs.add(new ContainsDigitSpecification());
        String specialChars = config.specialChars();
        if (specialChars != null && !specialChars.isBlank()) specs.add(new ContainsSpecialCharSpecification(specialChars));
        this.specifications = List.copyOf(specs);
    }

    @Override
    public List<PasswordSpecification2> specifications() {
        return specifications;
    }
}
