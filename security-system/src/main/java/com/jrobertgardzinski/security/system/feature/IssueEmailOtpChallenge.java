package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.MfaConfig;
import com.jrobertgardzinski.security.domain.repository.EmailOtpSenderPort;
import com.jrobertgardzinski.security.domain.repository.OtpCodeGenerator;
import com.jrobertgardzinski.security.domain.repository.OtpCodeHasher;
import com.jrobertgardzinski.security.domain.vo.EmailOtpChallenge;
import com.jrobertgardzinski.security.domain.vo.HashedOtpCode;
import com.jrobertgardzinski.security.domain.vo.OtpCode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * Generates a fresh OTP, hashes it for storage, sends the plaintext code
 * to the user's mailbox, and returns the challenge to attach to the auth session.
 */
public class IssueEmailOtpChallenge implements Function<Email, EmailOtpChallenge> {

    private final OtpCodeGenerator generator;
    private final OtpCodeHasher hasher;
    private final EmailOtpSenderPort sender;
    private final Clock clock;
    private final MfaConfig config;

    public IssueEmailOtpChallenge(OtpCodeGenerator generator, OtpCodeHasher hasher,
                                  EmailOtpSenderPort sender, Clock clock, MfaConfig config) {
        this.generator = generator;
        this.hasher = hasher;
        this.sender = sender;
        this.clock = clock;
        this.config = config;
    }

    @Override
    public EmailOtpChallenge apply(Email recipient) {
        OtpCode code = generator.generate();
        HashedOtpCode codeHash = hasher.hash(code);
        sender.send(recipient, code);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(config.otpExpiryMinutes().value());
        return new EmailOtpChallenge(codeHash, expiresAt);
    }
}
