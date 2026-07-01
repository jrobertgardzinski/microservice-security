package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Production notifier (outside the {@code test} environment): sends the password-reset link by
 * asking the standalone {@code microservice-email} service to e-mail it, via
 * {@link EmailServiceClient}. Replaces the earlier logging placeholder.
 */
@Singleton
@Requires(notEnv = "test")
final class HttpPasswordResetNotifier implements PasswordResetNotifier {

    private final EmailServiceClient client;
    private final String apiKey;
    private final String resetLinkBase;

    HttpPasswordResetNotifier(EmailServiceClient client,
                              @Value("${email-service.api-key}") String apiKey,
                              @Value("${email-service.reset-link-base}") String resetLinkBase) {
        this.client = client;
        this.apiKey = apiKey;
        this.resetLinkBase = resetLinkBase;
    }

    @Override
    public void sendResetLink(Email email, PasswordResetToken token) {
        client.sendPasswordResetLink(apiKey, Map.of(
                "to", email.value(), "link", resetLinkBase + token.value()));
    }
}
