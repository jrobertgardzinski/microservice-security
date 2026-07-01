package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder production notifier (outside the {@code test} environment): logs the reset link
 * instead of sending a real e-mail. A real SMTP adapter would replace this.
 */
@Singleton
@Requires(notEnv = "test")
final class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    @Override
    public void sendResetLink(Email email, PasswordResetToken token) {
        LOG.info("Password reset requested for {} — link: /reset-password (token issued)", email.value());
    }
}
