package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("MFA — e-mail code factor")
class EmailCodeFactorTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-05T10:00:00Z"));
    private final Clock clock = new Clock() {
        public Instant instant() { return now.get(); }
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
    };

    private String sentTo;
    private String sentCode;
    private final CodeChannel channel = new CodeChannel() {
        public FactorType servesFactor() { return FactorType.EMAIL_CODE; }
        public void sendCode(String target, String code) { sentTo = target; sentCode = code; }
    };
    // a transparent hasher so the test can reason about codes; production uses SHA-256
    private final CodeHasher hasher = raw -> "H(" + raw + ")";
    private final EmailCodeFactor factor = new EmailCodeFactor(
            channel, hasher, new ChallengeCodeConfig(5, 5, 6), clock);

    private final EnrolledFactor enrolment =
            new EnrolledFactor(Email.of("user@example.com"), FactorType.EMAIL_CODE, "e-mail code", 0, "user@example.com");

    @Test
    @DisplayName("issuing sends a 6-digit code to the target and remembers only its hash")
    void issue_sends_and_hashes() {
        Optional<Challenge> challenge = factor.issueChallenge(enrolment);

        assertTrue(challenge.isPresent());
        assertEquals("user@example.com", sentTo);
        assertTrue(sentCode.matches("\\d{6}"), "a 6-digit numeric code, got: " + sentCode);
        assertEquals("H(" + sentCode + ")", challenge.get().codeHash(), "the challenge keeps the hash, not the code");
    }

    @Test
    @DisplayName("the mailed code verifies; a wrong or expired code does not")
    void verify_matches_only_the_right_live_code() {
        Challenge challenge = factor.issueChallenge(enrolment).orElseThrow();

        assertTrue(factor.verify(enrolment, Optional.of(challenge), sentCode), "the mailed code passes");
        assertFalse(factor.verify(enrolment, Optional.of(challenge), "000000"), "a wrong code fails");

        now.set(now.get().plus(Duration.ofMinutes(6)));
        assertFalse(factor.verify(enrolment, Optional.of(challenge), sentCode), "past its TTL, even the right code fails");
    }

    @Test
    @DisplayName("e-mail is a challenge-response factor")
    void needs_a_challenge() {
        assertTrue(factor.needsChallenge());
        assertEquals(FactorType.EMAIL_CODE, factor.type());
    }
}
