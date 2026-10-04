package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;

import java.util.List;

/**
 * A user's recovery codes — single-use stand-ins for any MFA chain link they lost access to.
 * Only hashes are stored; the plain codes exist once, at generation, on the user's screen.
 */
public interface RecoveryCodeRepository {

    /** Store a fresh batch of code hashes, invalidating whatever the user had before. */
    void replaceAll(Email userEmail, List<String> codeHashes);

    /** Spend the code with this hash: true if it existed unused (it is now used up). */
    boolean consume(Email userEmail, String codeHash);

    /** How many codes remain unspent — the UI's "you have N left". */
    int unusedCount(Email userEmail);

    /** Remove every code a user has — the teardown when the account is deleted. */
    void removeAll(Email userEmail);

    /**
     * Moves every code of an account to a new address — this table is keyed by the e-mail, and the
     * e-mail is mutable, so the codes must FOLLOW the account, or the break-glass codes the user
     * printed stop working the moment they change their address (and stay usable under the old one).
     * Spent codes move as well: the count the UI shows must survive the move.
     */
    void reassign(Email fromEmail, Email toEmail);
}
