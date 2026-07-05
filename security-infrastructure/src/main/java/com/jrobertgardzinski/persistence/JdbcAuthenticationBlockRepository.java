package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
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
        String ip = authenticationBlock.source().ipAddress().value();
        repository.deleteById(ip); // upsert: drop any prior block for this source first
        repository.save(new AuthenticationBlockEntity(ip, authenticationBlock.expiryDate()));
        return authenticationBlock;
    }

    @Override
    public void removeAllFor(Source source) {
        repository.deleteById(source.ipAddress().value());
    }

    @Override
    public Optional<AuthenticationBlock> findBy(Source source) {
        // only the identity is stored for blocks; the reloaded Source carries no observed context
        return repository.findById(source.ipAddress().value())
                .map(entity -> new AuthenticationBlock(
                        Source.of(new IpAddress(entity.ipAddress())), entity.expiryDate()));
    }
}
