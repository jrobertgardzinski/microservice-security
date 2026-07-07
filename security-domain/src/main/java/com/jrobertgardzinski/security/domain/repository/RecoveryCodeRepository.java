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
}
