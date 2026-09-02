package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.password.security.config.MinLength;
import com.jrobertgardzinski.password.settings.MinPasswordLengthStore;
import com.jrobertgardzinski.password.settings.SetMinPasswordLength;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime rung without a database: vacant unless a test seeds it or an admin sets it. Mirrors
 * the other InMemory* fallbacks — the service boots and behaves identically, resolution simply
 * falls through to properties and the hardcoded default. Both sides of the rung live here: the
 * ladder reads through {@link LiveConfigPort}, the admin use case writes through
 * {@link MinPasswordLengthStore}, and {@link #put} is the test's stand-in for a hand at the
 * database console — it bypasses the value object on purpose.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemorySecuritySettings implements LiveConfigPort<Integer>, MinPasswordLengthStore {

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
