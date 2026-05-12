package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byEmail = new HashMap<>();

    @Override
    public boolean existsBy(Email email) {
        return byEmail.containsKey(email.value());
    }

    @Override
    public Optional<User> findBy(Email email) {
        return Optional.ofNullable(byEmail.get(email.value()));
    }

    @Override
    public User save(User user) {
        byEmail.put(user.email().value(), user);
        return user;
    }

    int size() {
        return byEmail.size();
    }
}
