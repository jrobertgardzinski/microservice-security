package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

import java.util.Optional;

/**
 * Holds at most one stored refresh-token expiration (one user per scenario), so
 * {@code findRefreshTokenExpirationBy} can answer active / expired / missing.
 */
public final class InMemoryAuthorizationDataRepository implements AuthorizationDataRepository {

    private Email email;
    private RefreshTokenExpiration expiration;
    private SessionTokens created;

    /** Test seam: seed a stored refresh token for the given user. */
    public void store(Email email, RefreshTokenExpiration expiration) {
        this.email = email;
        this.expiration = expiration;
    }

    @Override
    public SessionTokens create(SessionTokens sessionTokens) {
        this.created = sessionTokens;
        return sessionTokens;
    }

    @Override
    public void deleteBy(Email email) {
        this.email = null;
        this.expiration = null;
    }

    @Override
    public RefreshTokenExpiration findRefreshTokenExpirationBy(Email email, RefreshToken refreshToken) {
        return expiration != null && this.email != null && this.email.value().equals(email.value())
                ? expiration
                : null;
    }

    @Override
    public Optional<SessionTokens> findBy(Email email) {
        return Optional.ofNullable(created);
    }
}
