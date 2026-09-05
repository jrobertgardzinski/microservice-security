package com.jrobertgardzinski.security.custom.password.persistence;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.security.custom.password.MinLengthRepository;
import com.jrobertgardzinski.security.custom.password.SetMinPasswordLength;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live level without a database: vacant unless a test seeds it or an admin sets it. Mirrors
 * the other InMemory* fallbacks — the service boots and behaves identically, resolution simply
 * falls through to the property and the default. Both sides of the level live here: the ladder
 * reads through {@link LiveConfigPort}, the admin use case writes through
 * {@link MinLengthRepository}, and {@link #put} is the test's stand-in for a hand at the database
 * console — it bypasses the value object on purpose.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemorySecuritySettings implements LiveConfigPort<Integer>, MinLengthRepository {

    private final Map<String, Integer> rows = new ConcurrentHashMap<>();

    @Override
    public Integer find(String name) {
        return rows.get(name);
    }

    @Override
    public void save(MinLength minLength) {
        rows.put(SetMinPasswordLength.KEY, minLength.value());
    }

    public void put(String name, int value) {
        rows.put(name, value);
    }

    public void remove(String name) {
        rows.remove(name);
    }
}
