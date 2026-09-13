package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirming an enrolment is worth a fixed number of wrong proofs, and then the attempt is over.
 *
 * <p>There used to be no cap at all: the entry survived every wrong answer, so a six-digit code
 * could be walked through at the speed of the network for as long as the entry lived. What kept
 * that small was an elevated session and a short TTL — real mitigations, and neither of them a
 * reason for the count to be unbounded. Sign-in has answered this for years; enrolment now answers
 * it the same way.
 */
@Epic("Multi-factor sign-in")
@Feature("Enrolment")
class EnrolmentAttemptsTest {

    private static final Email USER = Email.of("enrolling@example.com");

    @Test
    @DisplayName("wrong proofs are counted, and running out ends the enrolment")
    void a_wrong_proof_costs_an_attempt() {
        RememberedCodes channel = new RememberedCodes();
        EnrolFactor enrol = new EnrolFactor(
                new FactorRegistry(List.of(new CodeFactor(
                        channel, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), Clock.systemUTC()))),
                new FactorStore(),
                new PendingEnrolments());

        enrol.start(USER, FactorType.EMAIL_CODE, USER.value());

        for (int attempt = 1; attempt <= EnrolmentChallengeStore.PendingEnrolment.ATTEMPTS; attempt++) {
            assertThat(enrol.confirm(USER, FactorType.EMAIL_CODE, "000000"))
                    .as("attempt %d is simply wrong, and the enrolment is still open", attempt)
                    .isInstanceOf(EnrolFactor.Result.WrongProof.class);
        }

        assertThat(enrol.confirm(USER, FactorType.EMAIL_CODE, channel.lastCode))
                .as("the attempts are spent, so even the RIGHT code finds nothing pending — a"
                        + " guesser has to start a new enrolment, which is itself guarded and"
                        + " throttled")
                .isInstanceOf(EnrolFactor.Result.NoPendingEnrolment.class);
    }

    @Test
    @DisplayName("a typo does not cost the enrolment")
    void the_right_code_still_works_after_a_mistake() {
        RememberedCodes channel = new RememberedCodes();
        EnrolFactor enrol = new EnrolFactor(
                new FactorRegistry(List.of(new CodeFactor(
                        channel, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), Clock.systemUTC()))),
                new FactorStore(),
                new PendingEnrolments());

        enrol.start(USER, FactorType.EMAIL_CODE, USER.value());
        enrol.confirm(USER, FactorType.EMAIL_CODE, "000000");

        assertThat(enrol.confirm(USER, FactorType.EMAIL_CODE, channel.lastCode))
                .as("people mistype; the cap is for the machine that does not")
                .isInstanceOf(EnrolFactor.Result.Enrolled.class);
    }

    /** A channel that keeps the code instead of sending it. */
    private static final class RememberedCodes implements CodeChannel {

        private String lastCode;

        @Override
        public FactorType servesFactor() {
            return FactorType.EMAIL_CODE;
        }

        @Override
        public void sendCode(String target, String code) {
            this.lastCode = code;
        }
    }

    /** The smallest store that answers what enrolment asks, in chain order. */
    private static final class FactorStore implements EnrolledFactorRepository {

        private final List<EnrolledFactor> rows = new ArrayList<>();

        @Override
        public List<EnrolledFactor> findByUser(Email userEmail) {
            return rows.stream()
                    .filter(row -> row.userEmail().equals(userEmail))
                    .sorted(Comparator.comparingInt(EnrolledFactor::order))
                    .toList();
        }

        @Override
        public void enrol(EnrolledFactor factor) {
            rows.removeIf(row -> row.userEmail().equals(factor.userEmail()) && row.type().equals(factor.type()));
            rows.add(factor);
        }

        @Override
        public void remove(Email userEmail, FactorType type) {
            rows.removeIf(row -> row.userEmail().equals(userEmail) && row.type().equals(type));
        }

        @Override
        public void removeAll(Email userEmail) {
            rows.removeIf(row -> row.userEmail().equals(userEmail));
        }

        @Override
        public void reassign(Email fromEmail, Email toEmail) {
            List<EnrolledFactor> moving = findByUser(fromEmail);
            rows.removeAll(moving);
            moving.forEach(row -> rows.add(new EnrolledFactor(
                    toEmail, row.type(), row.label(), row.order(), row.secretMaterial())));
        }
    }

    /** Pending enrolments in a map; expiry is somebody else's test. */
    private static final class PendingEnrolments implements EnrolmentChallengeStore {

        private final Map<String, PendingEnrolment> held = new HashMap<>();

        @Override
        public void put(Email user, FactorType type, PendingEnrolment enrolment) {
            held.put(key(user, type), enrolment);
        }

        @Override
        public Optional<PendingEnrolment> get(Email user, FactorType type) {
            return Optional.ofNullable(held.get(key(user, type)));
        }

        @Override
        public void remove(Email user, FactorType type) {
            held.remove(key(user, type));
        }

        private static String key(Email user, FactorType type) {
            return user.value() + "|" + type.value();
        }
    }
}
