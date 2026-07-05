package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;

/**
 * PostgreSQL-backed {@link EnrolledFactorRepository}. Re-enrolling the same (user, type) replaces
 * the row (upsert by the composite id). Active only when a datasource is present.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcEnrolledFactorRepository implements EnrolledFactorRepository {

    private final EnrolledFactorJdbcRepository repository;

    JdbcEnrolledFactorRepository(EnrolledFactorJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EnrolledFactor> findByUser(Email userEmail) {
        return repository.findByUserEmailOrderByFactorOrder(userEmail.value()).stream()
                .map(e -> new EnrolledFactor(Email.of(e.userEmail()), FactorType.of(e.type()),
                        e.label(), e.factorOrder(), e.secretMaterial()))
                .toList();
    }

    @Override
    public void enrol(EnrolledFactor factor) {
        String id = EnrolledFactorEntity.keyOf(factor.userEmail().value(), factor.type().value());
        repository.deleteById(id); // upsert same (user, type)
        repository.save(new EnrolledFactorEntity(id, factor.userEmail().value(), factor.type().value(),
                factor.label(), factor.order(), factor.secretMaterial()));
    }

    @Override
    public void remove(Email userEmail, FactorType type) {
        repository.deleteById(EnrolledFactorEntity.keyOf(userEmail.value(), type.value()));
    }
}
