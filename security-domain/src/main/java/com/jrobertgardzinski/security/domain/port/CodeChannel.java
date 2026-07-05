package com.jrobertgardzinski.security.domain.port;

import com.jrobertgardzinski.security.domain.vo.FactorType;

/**
 * Outbound port that delivers a short one-time code to a target (an e-mail address, a phone
 * number). One channel serves one factor type; the adapter decides the medium (an outbox mail
 * event, an SMS gateway, a log in tests). The raw code leaves through here and is never stored —
 * only its hash is kept, against the challenge.
 */
public interface CodeChannel {

    FactorType servesFactor();

    void sendCode(String target, String code);
}
