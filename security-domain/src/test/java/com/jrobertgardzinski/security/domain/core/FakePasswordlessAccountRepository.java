package com.jrobertgardzinski.security.domain.core;

import com.jrobertgardzinski.email.domain.Email;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory passwordless-account flag for the application-level scenarios. */
public final class FakePasswordlessAccountRepository implements PasswordlessAccountRepository {

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

    @Override
    public void reassign(Email fromEmail, Email toEmail) {
        if (passwordless.remove(fromEmail.value())) {
            passwordless.add(toEmail.value());
        }
    }

    @Override
    public void purge(Email email) {
        passwordless.remove(email.value());
    }
}
