package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory {@link EnrolledFactorRepository} used when no database is configured (tests). The JDBC
 * adapter takes over once a datasource is present. Keyed by the string value of the email.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryEnrolledFactorRepository implements EnrolledFactorRepository {

    private final Map<String, List<EnrolledFactor>> byUser = new ConcurrentHashMap<>();

    @Override
    public List<EnrolledFactor> findByUser(Email userEmail) {
        return byUser.getOrDefault(userEmail.value(), List.of()).stream()
                .sorted(Comparator.comparingInt(EnrolledFactor::order))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void enrol(EnrolledFactor factor) {
        byUser.compute(factor.userEmail().value(), (email, existing) -> {
            List<EnrolledFactor> list = existing == null ? new CopyOnWriteArrayList<>() : existing;
            list.removeIf(f -> f.type().equals(factor.type()));   // replace same type
            list.add(factor);
            return list;
        });
    }

    @Override
    public void remove(Email userEmail, FactorType type) {
        List<EnrolledFactor> list = byUser.get(userEmail.value());
        if (list != null) {
            list.removeIf(f -> f.type().equals(type));
        }
    }

    @Override
    public void removeAll(Email userEmail) {
        byUser.remove(userEmail.value());
    }
}
