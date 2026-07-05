package com.jrobertgardzinski;

import com.jrobertgardzinski.security.system.mfa.StepUpStore;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link StepUpStore}, keyed by a random one-shot ticket — the sign-in store's twin. */
@Singleton
final class InMemoryStepUpStore implements StepUpStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, StepUpPending> byTicket = new ConcurrentHashMap<>();

    @Override
    public String open(StepUpPending pending) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byTicket.put(ticket, pending);
        return ticket;
    }

    @Override
    public Optional<StepUpPending> find(String ticket) {
        return Optional.ofNullable(byTicket.get(ticket));
    }

    @Override
    public void replace(String ticket, StepUpPending pending) {
        byTicket.put(ticket, pending);
    }

    @Override
    public void close(String ticket) {
        byTicket.remove(ticket);
    }
}
