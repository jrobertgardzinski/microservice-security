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

    @Override
    public void removeAll(Email userEmail) {
        repository.findByUserEmailOrderByFactorOrder(userEmail.value())
                .forEach(e -> repository.deleteById(e.id()));
    }

    /**
     * Delete-then-insert rather than an UPDATE: the primary key is the flattened {@code email|type},
     * so a moved row needs a new id. A code factor targeting the account's own address is re-targeted
     * as well — {@code secret_material} is where the next code is SENT, so leaving the old address
     * there would keep mailing the codes to a mailbox the account no longer owns.
     */
    @Override
    public void reassign(Email fromEmail, Email toEmail) {
        List<EnrolledFactorEntity> moving = repository.findByUserEmailOrderByFactorOrder(fromEmail.value());
        moving.forEach(e -> repository.deleteById(e.id()));
        repository.saveAll(moving.stream()
                .map(e -> new EnrolledFactorEntity(
                        EnrolledFactorEntity.keyOf(toEmail.value(), e.type()), toEmail.value(), e.type(),
                        e.label(), e.factorOrder(),
                        fromEmail.value().equals(e.secretMaterial()) ? toEmail.value() : e.secretMaterial()))
                .toList());
    }
}
