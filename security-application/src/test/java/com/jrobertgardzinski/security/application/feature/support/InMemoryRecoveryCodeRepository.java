package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory recovery codes for the feature glue: hash -> spent?, keyed by email. */
public final class InMemoryRecoveryCodeRepository implements RecoveryCodeRepository {

    private final Map<String, Map<String, Boolean>> byUser = new HashMap<>();

    @Override
    public void replaceAll(Email userEmail, List<String> codeHashes) {
        Map<String, Boolean> fresh = new HashMap<>();
        codeHashes.forEach(hash -> fresh.put(hash, Boolean.FALSE));
        byUser.put(userEmail.value(), fresh);
    }

    @Override
    public boolean consume(Email userEmail, String codeHash) {
        Map<String, Boolean> codes = byUser.get(userEmail.value());
        return codes != null && codes.replace(codeHash, Boolean.FALSE, Boolean.TRUE);
    }

    @Override
    public int unusedCount(Email userEmail) {
        return (int) byUser.getOrDefault(userEmail.value(), Map.of())
                .values().stream().filter(spent -> !spent).count();
    }

    @Override
    public void removeAll(Email userEmail) {
        byUser.remove(userEmail.value());
    }
}
