package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link UserRepository}: a thin adapter mapping the {@code users} table rows
 * (see {@link UserEntity}) to and from the domain {@link User}. Active only when a datasource is
 * present; otherwise the in-memory repository serves.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcUserRepository implements UserRepository {

    private final UserJdbcRepository repository;

    JdbcUserRepository(UserJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findBy(Email email) {
        return repository.findByEmail(email.value()).map(JdbcUserRepository::toDomain);
    }

    @Override
    public boolean existsBy(NormalizedEmail normalizedEmail) {
        return repository.existsByNormalizedEmail(normalizedEmail.value());
    }

    @Override
    public void updatePassword(Email email, HashedPassword passwordHash) {
        repository.updatePassword(email.value(), passwordHash.value());
    }

    @Override
    public void updateEmail(Email currentEmail, Email newEmail) {
        repository.updateEmail(currentEmail.value(), newEmail.value(), NormalizedEmail.of(newEmail).value());
    }

    @Override
    public void deleteByEmail(Email email) {
        repository.deleteByEmail(email.value());
    }

    @Override
    public User save(User user) {
        try {
            repository.save(new UserEntity(
                    user.id(), user.email().value(), user.normalizedEmail().value(), user.passwordHash().value()));
            return user;
        } catch (DataAccessException e) {
            if (isUniqueViolation(e)) {
                throw new EmailAlreadyTakenException();
            }
            throw e;
        }
    }

    /** PostgreSQL SQLSTATE 23505 = unique_violation, here the email / normalized-email constraint. */
    private static boolean isUniqueViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static User toDomain(UserEntity entity) {
        Email email = Email.of(entity.email());
        return new User(entity.id(), email, new HashedPassword(entity.passwordHash()), NormalizedEmail.of(email));
    }
}
