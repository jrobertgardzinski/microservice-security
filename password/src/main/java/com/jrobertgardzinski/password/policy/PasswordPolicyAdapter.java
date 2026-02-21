package com.jrobertgardzinski.password.policy;

import com.jrobertgardzinski.password.domain.PasswordPolicy;
import com.jrobertgardzinski.password.domain.PasswordSpecification;
import com.jrobertgardzinski.password.specifications.ContainsDigitSpecification;
import com.jrobertgardzinski.password.specifications.ContainsLowercaseSpecification;
import com.jrobertgardzinski.password.specifications.MinLengthSpecification;
import com.jrobertgardzinski.password.specifications.ContainsSpecialCharSpecification;
import com.jrobertgardzinski.password.specifications.ContainsUppercaseSpecification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a PasswordPolicy from a PasswordPolicyConfig.
 * No-arg constructor uses the default configuration.
 */
public final class PasswordPolicyAdapter implements PasswordPolicy {

    private final List<PasswordSpecification> specifications;

    public PasswordPolicyAdapter() {
        this(PasswordPolicyConfig.builder().build());
    }

    public PasswordPolicyAdapter(PasswordPolicyConfig config) {
        List<PasswordSpecification> specs = new ArrayList<>();
        specs.add(new MinLengthSpecification(config.minLength()));
        if (config.requireLowercase()) specs.add(new ContainsLowercaseSpecification());
        if (config.requireUppercase()) specs.add(new ContainsUppercaseSpecification());
        if (config.requireDigit()) specs.add(new ContainsDigitSpecification());
        String specialChars = config.specialChars();
        if (specialChars != null && !specialChars.isBlank()) {
            specs.add(new ContainsSpecialCharSpecification(specialChars));
        }
        this.specifications = List.copyOf(specs);
    }

    @Override
    public List<PasswordSpecification> specifications() {
        return specifications;
    }
}
