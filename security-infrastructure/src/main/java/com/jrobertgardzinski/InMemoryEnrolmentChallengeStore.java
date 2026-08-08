package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.system.mfa.EnrolmentChallengeStore;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Half-finished enrolments (a code went out, the proof has not returned) held in memory, keyed by
 * user + factor type. Short-lived; a lost entry only means the user restarts the enrolment.
 */
@Singleton
final class InMemoryEnrolmentChallengeStore implements EnrolmentChallengeStore {

    private final Map<String, PendingEnrolment> byKey = new ConcurrentHashMap<>();

    @Override
    public void put(Email user, FactorType type, PendingEnrolment enrolment) {
        byKey.put(key(user, type), enrolment);
    }

    @Override
    public Optional<PendingEnrolment> get(Email user, FactorType type) {
        return Optional.ofNullable(byKey.get(key(user, type)));
    }

    @Override
    public void remove(Email user, FactorType type) {
        byKey.remove(key(user, type));
    }

    private static String key(Email user, FactorType type) {
        return user.value() + "|" + type.value();
    }
}
