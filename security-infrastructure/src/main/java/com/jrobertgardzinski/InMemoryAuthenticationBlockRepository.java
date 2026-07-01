package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AuthenticationBlockRepository} used when no database is configured (tests), keyed
 * by source IP (at most one block per source). The JDBC adapter takes over once a datasource exists.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryAuthenticationBlockRepository implements AuthenticationBlockRepository {

    private final Map<IpAddress, AuthenticationBlock> byIp = new ConcurrentHashMap<>();

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
