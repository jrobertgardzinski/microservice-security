package com.jrobertgardzinski.security.config.account;

import java.time.Duration;

/**
 * How an account deletion waits for the content it takes with it.
 *
 * @param purgeTimeout     how long a deletion waits for the portal's outcome before it gives up
 *                         and unlocks the account — the safety net for a portal that died, which
 *                         must fire well AFTER the portal's own worst case
 * @param awaitPortalPurge false for an identity-only deployment (no portal, e.g. security and the
 *                         game): there is no content to purge anywhere, so an account deletes at once
 */
public record AccountDeletionConfig(Duration purgeTimeout, boolean awaitPortalPurge) {
}
