package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.Source;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AuthenticationBlockRepository} used when no database is configured (tests), keyed
 * by the source's identity (its IP — at most one block per source). The JDBC adapter takes over once a datasource exists.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryAuthenticationBlockRepository implements AuthenticationBlockRepository {

    private final Map<Source, AuthenticationBlock> bySource = new ConcurrentHashMap<>();

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
