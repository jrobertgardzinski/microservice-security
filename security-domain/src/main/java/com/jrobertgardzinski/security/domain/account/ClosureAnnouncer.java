package com.jrobertgardzinski.security.domain.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.identity.UserId;

import java.util.UUID;

/**
 * What an account closure tells the world: the fact that it was requested, for the services that
 * hold the leaver's content, and the mail that closes it for the leaver — goodbye when it went
 * through, an apology when it did not. Durable with the state change that caused it, so a crash
 * between the two cannot leave one without the other.
 */
public interface ClosureAnnouncer {

    /**
     * Announces that {@code closure} was requested, as saga {@code sagaId}.
     *
     * @param leaver the account's id, beside its address — what the content services key their
     *               rows on; null only for an account this service cannot find
     */
    void announce(AccountClosure closure, UUID sagaId, UserId leaver);

    /** The account is gone for good. */
    void goodbye(Email leaver);

    /** The deletion did not happen; the account is unlocked again. */
    void apology(Email leaver);
}
