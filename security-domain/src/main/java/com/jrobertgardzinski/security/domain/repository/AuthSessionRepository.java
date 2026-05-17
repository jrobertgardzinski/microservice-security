package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.entity.AuthSession;
import com.jrobertgardzinski.security.domain.vo.AuthSessionId;

import java.util.Optional;

public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findBy(AuthSessionId id);

    void removeBy(AuthSessionId id);
}
