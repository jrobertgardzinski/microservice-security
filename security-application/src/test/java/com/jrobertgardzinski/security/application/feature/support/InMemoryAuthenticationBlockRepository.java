package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryAuthenticationBlockRepository implements AuthenticationBlockRepository {

    private final Map<IpAddress, AuthenticationBlock> byIp = new HashMap<>();

    @Override
    public AuthenticationBlock create(AuthenticationBlock authenticationBlock) {
        byIp.put(authenticationBlock.ipAddress(), authenticationBlock);
        return authenticationBlock;
    }

    @Override
    public void removeAllFor(IpAddress ipAddress) {
        byIp.remove(ipAddress);
    }

    @Override
    public Optional<AuthenticationBlock> findBy(IpAddress ipAddress) {
        return Optional.ofNullable(byIp.get(ipAddress));
    }
}
