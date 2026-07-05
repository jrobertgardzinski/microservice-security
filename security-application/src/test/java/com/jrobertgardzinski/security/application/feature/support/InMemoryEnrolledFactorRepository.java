package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory enrolled-factor repository for the application-level scenarios. */
public final class InMemoryEnrolledFactorRepository implements EnrolledFactorRepository {

    private final Map<String, List<EnrolledFactor>> byUser = new ConcurrentHashMap<>();

    @Override
    public List<EnrolledFactor> findByUser(Email userEmail) {
        return byUser.getOrDefault(userEmail.value(), List.of()).stream()
                .sorted(Comparator.comparingInt(EnrolledFactor::order))
                .toList();
    }

    @Override
    public void enrol(EnrolledFactor factor) {
        byUser.computeIfAbsent(factor.userEmail().value(), e -> new ArrayList<>());
        List<EnrolledFactor> list = byUser.get(factor.userEmail().value());
        list.removeIf(f -> f.type().equals(factor.type()));
        list.add(factor);
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
