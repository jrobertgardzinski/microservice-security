package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link PasswordlessAccountRepository} for the no-datasource (test) environment. */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryPasswordlessAccountRepository implements PasswordlessAccountRepository {

    private final Set<String> passwordless = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isPasswordless(Email email) {
        return passwordless.contains(email.value());
    }

    @Override
    public void setPasswordless(Email email, boolean value) {
        if (value) {
            passwordless.add(email.value());
        } else {
            passwordless.remove(email.value());
        }
    }
}
