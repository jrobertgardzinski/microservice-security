package com.jrobertgardzinski;

import java.time.Clock;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.vo.Source;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
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
    private final Clock clock;

    InMemoryAuthenticationBlockRepository(Clock clock) {
        this.clock = clock;
    }

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

    /**
     * Drop blocks that have served their time.
     *
     * <p>A block already stops mattering the moment it expires — the guard filters on
     * {@code isStillActive} — so this is about the map, not about the rule: without it every
     * address ever blocked stays remembered for as long as the process lives, and being blocked
     * costs an attacker nothing but three wrong guesses. The JDBC side has a reaper for exactly
     * this; the in-memory side is production wiring wherever no datasource is configured, so it
     * needs one too.
     */
    @Scheduled(fixedDelay = "5m")
    void evictExpired() {
        bySource.values().removeIf(block -> !block.isStillActive(clock));
    }
}
