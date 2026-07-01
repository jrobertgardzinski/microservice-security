package com.jrobertgardzinski;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * Pass-through boundary used when no datasource is configured (in-memory tests). There is no
 * transaction manager on that path, so the work simply runs as-is.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
final class NoTransactionBoundary implements TransactionBoundary {

    @Override
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
