package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.system.mfa.EnrolmentChallengeStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory enrolment-challenge store for the application-level scenarios. */
public final class InMemoryEnrolmentChallengeStore implements EnrolmentChallengeStore {

    private final Map<String, PendingEnrolment> byKey = new ConcurrentHashMap<>();

    @Override
    public void put(Email user, FactorType type, PendingEnrolment enrolment) {
        byKey.put(user.value() + "|" + type.value(), enrolment);
    }

    @Override
    public Optional<PendingEnrolment> get(Email user, FactorType type) {
        return Optional.ofNullable(byKey.get(user.value() + "|" + type.value()));
    }

    @Override
    public void remove(Email user, FactorType type) {
        byKey.remove(user.value() + "|" + type.value());
    }
}
