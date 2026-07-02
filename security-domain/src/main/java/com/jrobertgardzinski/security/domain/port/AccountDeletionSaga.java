package com.jrobertgardzinski.security.domain.port;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Outbound port that starts the cross-service part of closing an account: other services purge
 * the user's content, and their confirmation (or its absence) decides whether the deletion
 * completes or rolls back. How the request travels (an event, a queue) is the adapter's business.
 */
public interface AccountDeletionSaga {

    void begin(Email email);
}
