package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.password.security.config.MinLength;
import com.jrobertgardzinski.security.system.settings.MinPasswordLengthStore;
import com.jrobertgardzinski.security.system.settings.SetMinPasswordLength;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The write side of the {@code security_settings} runtime rung: one row per key, upserted, the
 * value stored as the text the reading adapter ({@link JdbcSecuritySettings}) parses back. Only a
 * {@link MinLength} gets in, so every row this class writes is one the ladder's gate will accept;
 * a row the gate refuses can only have come from elsewhere — a hand at the console — and the
 * ladder reports it as such. {@code updated_at} records when the decision was made.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcMinPasswordLengthStore implements MinPasswordLengthStore {

    private final SecuritySettingJdbcRepository repository;
    private final Clock clock;

    JdbcMinPasswordLengthStore(SecuritySettingJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
