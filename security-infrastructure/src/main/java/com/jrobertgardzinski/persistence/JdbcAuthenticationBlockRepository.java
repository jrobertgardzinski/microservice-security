package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link AuthenticationBlockRepository}. At most one block per source, so
 * {@code create} replaces any prior (e.g. expired) block for the same IP.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcAuthenticationBlockRepository implements AuthenticationBlockRepository {

    private final AuthenticationBlockJdbcRepository repository;

    JdbcAuthenticationBlockRepository(AuthenticationBlockJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuthenticationBlock create(AuthenticationBlock authenticationBlock) {
        String ip = authenticationBlock.ipAddress().value();
        repository.deleteById(ip); // upsert: drop any prior block for this source first
        repository.save(new AuthenticationBlockEntity(ip, authenticationBlock.expiryDate()));
        return authenticationBlock;
    }

    @Override
    public void removeAllFor(IpAddress ipAddress) {
        repository.deleteById(ipAddress.value());
    }

    @Override
    public Optional<AuthenticationBlock> findBy(IpAddress ipAddress) {
        return repository.findById(ipAddress.value())
                .map(entity -> new AuthenticationBlock(new IpAddress(entity.ipAddress()), entity.expiryDate()));
    }
}
