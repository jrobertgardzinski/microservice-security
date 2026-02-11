package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.List;
import java.util.Optional;

public sealed interface PasswordPolicyPort2 permits ConfigurablePasswordPolicyAdapter2 {

    List<PasswordSpecification2> specifications();

    default List<String> validate(PlaintextPassword2 password) {
        return specifications().stream()
                .map(spec -> spec.check(password))
                .flatMap(Optional::stream)
                .toList();
    }

    default boolean isSatisfiedBy(PlaintextPassword2 password) {
        return validate(password).isEmpty();
    }
}
