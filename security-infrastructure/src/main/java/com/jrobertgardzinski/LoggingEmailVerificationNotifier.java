package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder production notifier (outside the {@code test} environment): logs the verification link
 * instead of sending a real e-mail. A real SMTP adapter would replace this.
 */
@Singleton
@Requires(notEnv = "test")
final class LoggingEmailVerificationNotifier implements EmailVerificationNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailVerificationNotifier.class);

    @Override
    public void sendVerificationLink(Email email, VerificationToken token) {
        LOG.info("Email verification requested for {} — link: /verify-email (token issued)", email.value());
    }
}
