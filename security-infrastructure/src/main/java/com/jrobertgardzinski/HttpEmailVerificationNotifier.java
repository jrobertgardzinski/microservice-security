package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Production notifier (outside the {@code test} environment): sends the verification link by asking
 * the standalone {@code microservice-email} service to e-mail it, via {@link EmailServiceClient}.
 * Replaces the earlier logging placeholder.
 */
@Singleton
@Requires(notEnv = "test")
final class HttpEmailVerificationNotifier implements EmailVerificationNotifier {

    private final EmailServiceClient client;
    private final String apiKey;
    private final String verifyLinkBase;

    HttpEmailVerificationNotifier(EmailServiceClient client,
                                  @Value("${email-service.api-key}") String apiKey,
                                  @Value("${email-service.verify-link-base}") String verifyLinkBase) {
        this.client = client;
        this.apiKey = apiKey;
        this.verifyLinkBase = verifyLinkBase;
    }

    @Override
    public void sendVerificationLink(Email email, VerificationToken token) {
        client.sendVerificationLink(apiKey, Map.of(
                "to", email.value(), "link", verifyLinkBase + token.value()));
    }
}
