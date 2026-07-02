package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test double for {@link EmailVerificationRepository}: plain maps, raw token values as keys (test
 * scope, so no hashing).
 */
public class InMemoryEmailVerificationRepository implements EmailVerificationRepository {

    private final Map<String, String> pendingTokenByEmail = new HashMap<>();
    private final Map<String, Boolean> verifiedByEmail = new HashMap<>();

    @Override
    public void startVerification(Email email, VerificationToken token) {
        pendingTokenByEmail.put(email.value(), token.value());
        verifiedByEmail.put(email.value(), false);
    }

    @Override
    public Optional<Email> completeVerification(VerificationToken token) {
        return pendingTokenByEmail.entrySet().stream()
                .filter(e -> e.getValue().equals(token.value()))
                .findFirst()
                .map(e -> {
                    verifiedByEmail.put(e.getKey(), true);
                    pendingTokenByEmail.remove(e.getKey());
                    return Email.of(e.getKey());
                });
    }

    @Override
    public void markVerified(Email email) {
        pendingTokenByEmail.remove(email.value());
        verifiedByEmail.put(email.value(), true);
    }

    @Override
    public boolean isVerified(Email email) {
        return verifiedByEmail.getOrDefault(email.value(), false);
    }
}
