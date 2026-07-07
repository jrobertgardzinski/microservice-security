package com.jrobertgardzinski;

import java.util.HashMap;
import java.util.Map;

/**
 * The 202 body a factor step returns to the client. Beyond the ticket and which factor is next,
 * it may carry {@code challengeData} — the public data the client needs to answer that factor
 * (a WebAuthn nonce); code factors leave it out. A {@link HashMap} (not {@code Map.of}) so the
 * optional field can simply be absent rather than null.
 */
final class MfaBody {

    private MfaBody() {
    }

    /** The sign-in / continuation shape: {@code MFA_REQUIRED} with an {@code mfaTicket}. */
    static Map<String, Object> of(String ticket, String nextFactor, String challengeData) {
        return body("MFA_REQUIRED", "mfaTicket", ticket, nextFactor, challengeData);
    }

    /** The step-up shape: {@code FACTOR_REQUIRED} with a {@code stepUpTicket}. */
    static Map<String, Object> stepUp(String ticket, String nextFactor, String challengeData) {
        return body("FACTOR_REQUIRED", "stepUpTicket", ticket, nextFactor, challengeData);
    }

    private static Map<String, Object> body(String status, String ticketKey, String ticket,
                                            String nextFactor, String challengeData) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put(ticketKey, ticket);
        body.put("nextFactor", nextFactor);
        if (challengeData != null) {
            body.put("challengeData", challengeData);
        }
        return body;
    }
}
