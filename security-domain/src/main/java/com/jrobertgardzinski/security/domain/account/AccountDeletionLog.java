package com.jrobertgardzinski.security.domain.account;

import com.jrobertgardzinski.email.domain.Email;

import java.time.Duration;
import java.util.List;

/**
 * What an operator is told about the account-deletion saga. Each moment its own method, because
 * they are not equally loud: most are routine, and one of them needs a human.
 */
public interface AccountDeletionLog {

    /** No portal is configured, so the account was deleted on the spot. */
    void deletedImmediately(Email leaver);

    /** A deletion for this address was already running; the request joined it. */
    void alreadyUnderWay(Email leaver);

    /** The portal confirmed its purge, and the account is deleted for good. */
    void completed(Email leaver);

    /**
     * The portal confirmed a purge AFTER this service had given up, unlocked the account and
     * apologised: the account exists and its content does not. This needs a human.
     */
    void erasedAfterCompensation(Email leaver);

    /** A purge confirmation that matched no running deletion — a duplicate, most likely. */
    void strayConfirmation(Email leaver);

    /** A purge failure that matched no running deletion. */
    void strayFailure(Email leaver);

    /**
     * The portal reported a failed purge and the account is unlocked.
     *
     * @param reserved the portal participants that had already reserved the content and are
     *                 restoring it; empty when none had
     */
    void compensated(Email leaver, List<String> reserved);

    /** No outcome arrived within {@code timeout}, and the account is unlocked. */
    void compensatedOverdue(Email leaver, Duration timeout);
}
