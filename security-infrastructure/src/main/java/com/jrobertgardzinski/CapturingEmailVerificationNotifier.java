package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test notifier ({@code test} environment only): instead of sending an e-mail it captures the last
 * verification token issued per address, so a black-box test can read back the link it would have
 * received. Never active outside the {@code test} environment.
 */
@Singleton
@Requires(env = "test")
public final class CapturingEmailVerificationNotifier implements EmailVerificationNotifier {

    private final Map<String, String> lastTokenByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationLink(Email email, VerificationToken token) {
        lastTokenByEmail.put(email.value(), token.value());
    }

    public String lastTokenFor(String email) {
        return lastTokenByEmail.get(email);
    }
}
