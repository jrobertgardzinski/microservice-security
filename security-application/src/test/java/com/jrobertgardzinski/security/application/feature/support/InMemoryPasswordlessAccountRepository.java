package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory passwordless-account flag for the application-level scenarios. */
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
