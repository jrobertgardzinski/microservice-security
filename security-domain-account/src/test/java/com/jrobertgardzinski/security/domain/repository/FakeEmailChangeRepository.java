package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link EmailChangeRepository}: pending moves keyed by their ticket, confirmed once.
 *
 * <p>{@link #purge} drops a pending change that names this address at EITHER end, the same as both
 * real adapters: a move still pending for an address the account no longer owns is a live ticket
 * pointing at a stranger's account.
 *
 * <p>Keyed by the token object rather than a hash of it, for the reason
 * {@link FakePasswordResetRepository} gives.
 */
public final class FakeEmailChangeRepository implements EmailChangeRepository {

    private final Map<VerificationToken, PendingEmailChange> byToken = new HashMap<>();
    private final Clock clock;

    public FakeEmailChangeRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        byToken.put(token, new PendingEmailChange(change, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingEmailChange> confirmChange(VerificationToken token) {
        return Optional.ofNullable(byToken.remove(token));
    }

    @Override
    public void purge(Email email) {
        byToken.values().removeIf(pending -> email.equals(pending.change().currentEmail())
                || email.equals(pending.change().newEmail()));
    }

    /** Whether any pending move still names this address, as either end of it. */
    public boolean hasPendingChangeNaming(Email email) {
        return byToken.values().stream().anyMatch(pending ->
                email.equals(pending.change().currentEmail())
                        || email.equals(pending.change().newEmail()));
    }
}
