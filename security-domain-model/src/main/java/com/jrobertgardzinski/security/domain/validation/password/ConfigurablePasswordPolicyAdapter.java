package com.jrobertgardzinski.security.domain.validation.password;

import java.util.ArrayList;
import java.util.List;

public final class ConfigurablePasswordPolicyAdapter implements PasswordPolicyPort {

    private final List<PasswordSpecification> specifications;

    public ConfigurablePasswordPolicyAdapter() {
        this(PasswordPolicyConfig.builder().build());
    }

    public ConfigurablePasswordPolicyAdapter(PasswordPolicyConfig config) {
        List<PasswordSpecification> specs = new ArrayList<>();
        specs.add(new MinLengthSpecification(config.minLength()));
        if (config.requireLowercase()) specs.add(new ContainsLowercaseSpecification());
        if (config.requireUppercase()) specs.add(new ContainsUppercaseSpecification());
        if (config.requireDigit()) specs.add(new ContainsDigitSpecification());
        String specialChars = config.specialChars();
        if (specialChars != null && !specialChars.isBlank()) specs.add(new ContainsSpecialCharSpecification(specialChars));
        this.specifications = List.copyOf(specs);
    }

    @Override
    public List<PasswordSpecification> specifications() {
        return specifications;
    }
}
