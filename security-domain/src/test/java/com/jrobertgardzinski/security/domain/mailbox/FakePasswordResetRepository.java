package com.jrobertgardzinski.security.domain.mailbox;

import com.jrobertgardzinski.email.domain.Email;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link PasswordResetRepository}: one pending reset per address, consumed once.
 *
 * <p>It keys by the token OBJECT and not by a hash of it. Hashing is how an adapter avoids keeping
 * a live credential in a store it does not control; nothing in this process outlives the test
 * method, and the port promises single use, not a storage format. A fake that hashed would be
 * imitating the adapter's precaution rather than its promise.
 */
public final class FakePasswordResetRepository implements PasswordResetRepository {

    private record Row(PasswordResetToken token, LocalDateTime requestedAt) {}

    private final Map<String, Row> byEmail = new HashMap<>();
    private final Clock clock;

    public FakePasswordResetRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        byEmail.put(email.value(), new Row(token, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingReset> consumeReset(PasswordResetToken token) {
        for (Map.Entry<String, Row> pending : byEmail.entrySet()) {
            if (pending.getValue().token().equals(token)) {
                byEmail.remove(pending.getKey());
                return Optional.of(new PendingReset(Email.of(pending.getKey()),
                        pending.getValue().requestedAt()));
            }
        }
        return Optional.empty();
    }

    @Override
    public void purge(Email email) {
        byEmail.remove(email.value());
    }

    /** Whether a reset link issued for this address would still be redeemable. */
    public boolean hasPendingResetFor(Email email) {
        return byEmail.containsKey(email.value());
    }
}
