package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;

import java.util.Optional;

public interface UserRepository {

    boolean existsBy(Email email);

    Optional<User> findBy(Email email);

    User save(User user) throws Exception; // todo I don't like that exception
}
