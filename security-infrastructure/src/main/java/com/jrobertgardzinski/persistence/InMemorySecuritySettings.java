package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime rung without a database: vacant unless a test seeds it. Mirrors the other
 * InMemory* fallbacks — the service boots and behaves identically, resolution simply falls
 * through to properties and the hardcoded default.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemorySecuritySettings implements LiveConfigPort<Integer> {

    private final Map<String, Integer> rows = new ConcurrentHashMap<>();

    @Override
    public Integer find(String name) {
        return rows.get(name);
    }

    public void put(String name, int value) {
        rows.put(name, value);
    }

    public void remove(String name) {
        rows.remove(name);
    }
}
