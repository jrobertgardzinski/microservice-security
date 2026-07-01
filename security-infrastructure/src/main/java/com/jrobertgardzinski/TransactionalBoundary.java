package com.jrobertgardzinski;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * Real transaction boundary, active when a datasource is configured (dev/prod). The {@link
 * Transactional} method commits the wrapped work as one unit or rolls it back on failure.
 */
@Singleton
@Requires(beans = DataSource.class)
class TransactionalBoundary implements TransactionBoundary {

    @Override
    @Transactional
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
