package com.jrobertgardzinski;

import java.time.Clock;
import io.micronaut.scheduling.annotation.Scheduled;
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
    private final Clock clock;

    InMemoryEnrolmentChallengeStore(Clock clock) {
        this.clock = clock;
    }

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

    /**
     * Drop enrolments nobody came back to finish.
     *
     * <p>An entry is written when someone STARTS adding a factor and removed when they confirm —
     * so every abandoned attempt stays for ever, and starting one costs a request. The same shape
     * as the OAuth flow store that P18 poz. 17 found growing without a bound; the fix is the same,
     * and the law that now watches for it is StoresWithATtlEvictThemTest.
     */
    @Scheduled(fixedDelay = "5m")
    void evictAbandoned() {
        byKey.values().removeIf(enrolment -> enrolment.challenge().isExpired(clock));
    }

    private static String key(Email user, FactorType type) {
        return user.value() + "|" + type.value();
    }
}
