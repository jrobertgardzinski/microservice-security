package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test notifier ({@code test} environment only): captures the last reset token issued per address so
 * a black-box test can read back the link it would have received. Never active outside {@code test}.
 */
@Singleton
@Requires(env = "test")
public final class CapturingPasswordResetNotifier implements PasswordResetNotifier {

    private final Map<String, String> lastTokenByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendResetLink(Email email, PasswordResetToken token) {
        lastTokenByEmail.put(email.value(), token.value());
    }

    public String lastTokenFor(String email) {
        return lastTokenByEmail.get(email);
    }
}
