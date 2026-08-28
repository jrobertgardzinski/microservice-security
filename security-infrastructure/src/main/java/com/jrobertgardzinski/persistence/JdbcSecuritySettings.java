package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * PostgreSQL-backed runtime rung of the configuration ladder: reads {@code security_settings} by
 * key name and parses the text value as an integer. An unparseable value is logged and reported
 * as ABSENT — for the ladder that is a vacant rung to fall through, never a failure that could
 * take the calling use case down. (A parseable but ILLEGAL value — 3 for a minimum length whose
 * floor is 5 — is the ladder's own gate's business, logged and skipped there.)
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcSecuritySettings implements LiveConfigPort<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcSecuritySettings.class);

    private final SecuritySettingJdbcRepository repository;

    JdbcSecuritySettings(SecuritySettingJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Integer find(String name) {
        return repository.findById(name).map(row -> {
            try {
                return Integer.valueOf(row.value().trim());
            } catch (NumberFormatException unparseable) {
                LOG.warn("security_settings row '{}' holds non-numeric value '{}' - treating the rung as vacant",
                        name, row.value());
                return null;
            }
        }).orElse(null);
    }
}
