package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The factors this deployment offers, keyed by type — assembled from whichever
 * {@link AuthenticationFactor} beans are enabled. "Which methods does this service support" is
 * exactly "which adapters are wired", nothing more: dropping in a new factor bean makes it
 * available here with no other change.
 */
public class FactorRegistry {

    private final Map<FactorType, AuthenticationFactor> byType;

    public FactorRegistry(Collection<AuthenticationFactor> factors) {
        this.byType = factors.stream()
                .collect(Collectors.toUnmodifiableMap(AuthenticationFactor::type, Function.identity()));
    }

    public Optional<AuthenticationFactor> forType(FactorType type) {
        return Optional.ofNullable(byType.get(type));
    }

    public Set<FactorType> offered() {
        return byType.keySet();
    }
}
