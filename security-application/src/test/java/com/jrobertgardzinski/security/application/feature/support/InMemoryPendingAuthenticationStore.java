package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.system.mfa.PendingAuthentication;
import com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory pending-authentication store for the application-level scenarios. */
public final class InMemoryPendingAuthenticationStore implements PendingAuthenticationStore {

    private final Map<String, PendingAuthentication> byTicket = new ConcurrentHashMap<>();

    @Override
    public String open(PendingAuthentication pending) {
        String ticket = UUID.randomUUID().toString();
        byTicket.put(ticket, pending);
        return ticket;
    }

    @Override
    public Optional<PendingAuthentication> find(String ticket) {
        return Optional.ofNullable(byTicket.get(ticket));
    }

    @Override
    public void replace(String ticket, PendingAuthentication pending) {
        byTicket.put(ticket, pending);
    }

    @Override
    public void close(String ticket) {
        byTicket.remove(ticket);
    }
}
