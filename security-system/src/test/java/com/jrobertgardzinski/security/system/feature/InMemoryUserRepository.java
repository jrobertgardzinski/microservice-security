package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.SaveResult;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byEmail = new HashMap<>();

    @Override
    public Optional<User> findBy(Email email) {
        return Optional.ofNullable(byEmail.get(email.value()));
    }

    @Override
    public SaveResult save(User user) {
        if (byEmail.putIfAbsent(user.email().value(), user) != null) {
            return new SaveResult.AlreadyExists(user.email());
        }
        return new SaveResult.Saved(user);
    }

    int size() {
        return byEmail.size();
    }
}
