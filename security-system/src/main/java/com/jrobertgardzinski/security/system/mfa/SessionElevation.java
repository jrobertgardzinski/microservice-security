package com.jrobertgardzinski.security.system.mfa;

/**
 * A short-lived, one-shot "recently re-proven" mark for step-up authentication, keyed by the
 * caller's access token. A sensitive action mints it after the caller passes the step-up chain and
 * consumes it once — so a stolen live session cannot quietly delete an account: the thief would
 * have to pass the step-up too. In memory, TTL bounded (a lost mark just means proving again).
 */
public interface SessionElevation {

    void elevate(String accessToken);

    /** True and cleared if the token holds a live elevation; false otherwise (one-shot). */
    boolean consume(String accessToken);
}
