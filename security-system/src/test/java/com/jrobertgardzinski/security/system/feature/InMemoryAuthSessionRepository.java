package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.entity.AuthSession;
import com.jrobertgardzinski.security.domain.repository.AuthSessionRepository;
import com.jrobertgardzinski.security.domain.vo.AuthSessionId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryAuthSessionRepository implements AuthSessionRepository {

    private final Map<AuthSessionId, AuthSession> byId = new HashMap<>();

    @Override
    public AuthSession save(AuthSession session) {
        byId.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<AuthSession> findBy(AuthSessionId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public void removeBy(AuthSessionId id) {
        byId.remove(id);
    }

    public int size() {
        return byId.size();
    }
}
