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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every enrolled factor holds its own position in the sign-in chain.
 *
 * <p>A new factor used to be given the COUNT of the factors already enrolled, which is the position
 * past the last one only while nothing has ever been removed. Enrol two (0, 1), drop the FIRST, and
 * the count is 1 again — so the next enrolment lands on the position the survivor already holds,
 * and which of the two comes first in the chain is then whatever the store happens to return first.
 *
 * <p>Driven through {@link EnrolFactor} itself, because the arithmetic being pinned lives there;
 * re-deriving it in the test would prove only that the test can add.
 */
@Epic("Multi-factor sign-in")
@Feature("Chain order")
class ChainPositionsTest {

    private static final Email USER = Email.of("chain@example.com");

    @Test
    @DisplayName("a factor enrolled after a removal does not land on a position still in use")
    void positions_stay_unique_across_removals() {
        RememberedCodes email = new RememberedCodes(FactorType.EMAIL_CODE);
        RememberedCodes sms = new RememberedCodes(FactorType.SMS_CODE);
        FactorRegistry registry = new FactorRegistry(List.of(
                new CodeFactor(email, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), Clock.systemUTC()),
                new CodeFactor(sms, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), Clock.systemUTC())));
        FactorStore factors = new FactorStore();
        EnrolFactor enrol = new EnrolFactor(registry, factors, new PendingEnrolments());

        enrolFully(enrol, FactorType.EMAIL_CODE, "chain@example.com", email);
        enrolFully(enrol, FactorType.SMS_CODE, "+48111222333", sms);

        // the person drops the first factor and later adds it back — the shape that used to collide
        factors.remove(USER, FactorType.EMAIL_CODE);
        enrolFully(enrol, FactorType.EMAIL_CODE, "chain@example.com", email);

        assertThat(factors.findByUser(USER).stream().map(EnrolledFactor::order).toList())
                .as("two factors on one position leave the chain's order to the store's whim")
                .doesNotHaveDuplicates();
        assertThat(factors.findByUser(USER).stream().map(EnrolledFactor::type).toList())
                .as("and the one enrolled last is the one asked for last")
                .containsExactly(FactorType.SMS_CODE, FactorType.EMAIL_CODE);
    }

    private static void enrolFully(EnrolFactor enrol, FactorType type, String target, RememberedCodes channel) {
        enrol.start(USER, type, target);
        EnrolFactor.Result confirmed = enrol.confirm(USER, type, channel.lastCode);
        assertThat(confirmed).isInstanceOf(EnrolFactor.Result.Enrolled.class);
    }

    /** A channel that keeps the code instead of sending it, so the test can answer the challenge. */
    private static final class RememberedCodes implements CodeChannel {

        private final FactorType type;
        private String lastCode;

        private RememberedCodes(FactorType type) {
            this.type = type;
        }

        @Override
        public FactorType servesFactor() {
            return type;
        }

        @Override
        public void sendCode(String target, String code) {
            this.lastCode = code;
        }
    }

    /** The smallest store that answers the two questions enrolment asks, in chain order. */
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

    /** Pending enrolments, kept in a field; expiry is somebody else's test. */
    private static final class PendingEnrolments implements EnrolmentChallengeStore {

        private final java.util.Map<String, PendingEnrolment> held = new java.util.HashMap<>();

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
