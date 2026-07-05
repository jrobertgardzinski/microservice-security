package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryAuthenticationBlockRepository implements AuthenticationBlockRepository {

    private final Map<Source, AuthenticationBlock> bySource = new HashMap<>();

    @Override
    public AuthenticationBlock create(AuthenticationBlock authenticationBlock) {
        bySource.put(authenticationBlock.source(), authenticationBlock);
        return authenticationBlock;
    }

    @Override
    public void removeAllFor(Source source) {
        bySource.remove(source);
    }

    @Override
    public Optional<AuthenticationBlock> findBy(Source source) {
        return Optional.ofNullable(bySource.get(source));
    }
}
