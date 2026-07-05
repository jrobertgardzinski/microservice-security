package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The e-mail second factor: on {@link #issueChallenge} it mints a random numeric code, sends it
 * through the {@link CodeChannel} (an outbox mail event in production, a capturing double in
 * tests), and remembers only its hash and expiry. On {@link #verify} it checks the presented code
 * against that hash, refusing an expired one. Code length and TTL come from the config layer.
 *
 * <p>This is the reference challenge-response factor — SMS is the same shape over a different
 * channel, and both prove the registry is genuinely plug-and-play.
 */
public class EmailCodeFactor implements AuthenticationFactor {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodeChannel channel;
    private final CodeHasher hasher;
    private final ChallengeCodeConfig config;
    private final Clock clock;

    public EmailCodeFactor(CodeChannel channel, CodeHasher hasher, ChallengeCodeConfig config, Clock clock) {
        this.channel = channel;
        this.hasher = hasher;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public FactorType type() {
        return FactorType.EMAIL_CODE;
    }

    @Override
    public boolean needsChallenge() {
        return true;
    }

    @Override
    public Optional<Challenge> issueChallenge(EnrolledFactor enrolment) {
        String code = randomCode();
        channel.sendCode(enrolment.secretMaterial(), code);
        return Optional.of(new Challenge(
                hasher.hash(code),
                LocalDateTime.now(clock).plusMinutes(config.codeTtlMinutes())));
    }

    @Override
    public boolean verify(EnrolledFactor enrolment, Optional<Challenge> challenge, String proof) {
        return challenge
                .filter(c -> !c.isExpired(clock))
                .map(c -> c.codeHash().equals(hasher.hash(proof)))
                .orElse(false);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(config.codeLength());
        for (int i = 0; i < config.codeLength(); i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
