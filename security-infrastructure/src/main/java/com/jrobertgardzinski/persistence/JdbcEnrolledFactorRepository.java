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
 *
 * <p>A TOTP seed is encrypted on the way in and decrypted on the way out
 * ({@link com.jrobertgardzinski.TotpSecretCipher}) — it is the one piece of factor material that
 * mints credentials rather than receiving them, so a copy of this table would otherwise be a copy
 * of every authenticator app in it. The other factors' material is left as it is on purpose: an
 * address is where a code is SENT (and is matched by {@link #reassign} when an account moves), and
 * a passkey's public key is public by construction. Encryption happens HERE and not in the
 * in-memory twin because "at rest" is a claim about a disk, and that one has none.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcEnrolledFactorRepository implements EnrolledFactorRepository {

    private final EnrolledFactorJdbcRepository repository;
    private final com.jrobertgardzinski.TotpSecretCipher cipher;

    JdbcEnrolledFactorRepository(EnrolledFactorJdbcRepository repository,
                                 com.jrobertgardzinski.TotpSecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /** Only the seed that mints codes is a secret; an address and a public key are not. */
    private static boolean isASecret(String type) {
        return FactorType.TOTP.value().equals(type);
    }

    private String stored(EnrolledFactor factor) {
        return isASecret(factor.type().value())
                ? cipher.encrypt(factor.secretMaterial()) : factor.secretMaterial();
    }

    private String readable(EnrolledFactorEntity row) {
        return isASecret(row.type()) ? cipher.decrypt(row.secretMaterial()) : row.secretMaterial();
    }

    @Override
    public List<EnrolledFactor> findByUser(Email userEmail) {
        return repository.findByUserEmailOrderByFactorOrder(userEmail.value()).stream()
                .map(e -> new EnrolledFactor(Email.of(e.userEmail()), FactorType.of(e.type()),
                        e.label(), e.factorOrder(), readable(e)))
                .toList();
    }

    @Override
    public void enrol(EnrolledFactor factor) {
        String id = EnrolledFactorEntity.keyOf(factor.userEmail().value(), factor.type().value());
        repository.deleteById(id); // upsert same (user, type)
        repository.save(new EnrolledFactorEntity(id, factor.userEmail().value(), factor.type().value(),
                factor.label(), factor.order(), stored(factor)));
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
