package com.jrobertgardzinski.security.custom.password.persistence;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.security.custom.password.MinLengthRepository;
import com.jrobertgardzinski.security.custom.password.SetMinPasswordLength;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The {@code security_settings} table as one repository behind two ports: the live level of the
 * ladder, read and written through the same class, as its in-memory twin
 * {@link InMemorySecuritySettings} is.
 *
 * <p>Read side ({@link LiveConfigPort}): finds a row by key name and parses the text as an integer.
 * An unparseable value is logged and reported as ABSENT — a vacant level to fall through, never a
 * failure that could take the calling use case down. The read side hands back the RAW number on
 * purpose: the ladder's gate must be the one to refuse a parseable-but-illegal row, log it and say
 * so in its report.
 *
 * <p>Write side ({@link MinLengthRepository}): upserts the one row for the minimum length, the value
 * object already having said yes.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcSecuritySettings implements LiveConfigPort<Integer>, MinLengthRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcSecuritySettings.class);

    private final SecuritySettingJdbcRepository repository;
    private final Clock clock;

    JdbcSecuritySettings(SecuritySettingJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Integer find(String name) {
        return repository.findById(name).map(row -> {
            try {
                return Integer.valueOf(row.value().trim());
            } catch (NumberFormatException unparseable) {
                LOG.warn("security_settings row '{}' holds non-numeric value '{}' - treating the level as vacant",
                        name, row.value());
                return null;
            }
        }).orElse(null);
    }

    @Override
    public void save(MinLength minLength) {
        var row = new SecuritySettingEntity(SetMinPasswordLength.KEY,
                Integer.toString(minLength.value()), LocalDateTime.now(clock));
        if (repository.existsById(SetMinPasswordLength.KEY)) {
            repository.update(row);
        } else {
            repository.save(row);
        }
    }
}
