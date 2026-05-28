package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findBy(Email email);

    User save(User user);
}
