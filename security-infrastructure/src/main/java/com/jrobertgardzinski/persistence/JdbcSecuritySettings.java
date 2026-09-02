package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.password.security.config.MinLength;
import com.jrobertgardzinski.password.settings.MinLengthRepository;
import com.jrobertgardzinski.password.settings.SetMinPasswordLength;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The {@code security_settings} table as one repository behind two ports — the runtime rung of
 * the configuration ladder, read and written through the same class, as its in-memory twin
 * {@link InMemorySecuritySettings} already is.
 *
 * <p><b>Read side</b> ({@link LiveConfigPort}): finds a row by key name and parses the text value
 * as an integer. An unparseable value is logged and reported as ABSENT — for the ladder that is a
 * vacant rung to fall through, never a failure that could take the calling use case down. The two
 * sides speak different types on purpose: the read side hands back the RAW number, because the
 * ladder's gate must be the one to refuse a parseable-but-illegal row (3 for a minimum length
 * whose floor is 5), log it and fall through, and to say so in its report. Had this side built a
 * {@link MinLength} itself, the refusal would blow up here instead of being told.
 *
 * <p><b>Write side</b> ({@link MinLengthRepository}): one row per key, upserted, the value
 * stored as the text the read side parses back. Only a {@link MinLength} gets in, so every row
 * this class writes is one the gate will accept; a row the gate refuses can only have come from
 * elsewhere — a hand at the console — and the ladder reports it as such. {@code updated_at}
 * records when the decision was made.
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
                LOG.warn("security_settings row '{}' holds non-numeric value '{}' - treating the rung as vacant",
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
