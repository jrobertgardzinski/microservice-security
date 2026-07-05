package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Captures the last code "sent" to each target so a scenario can read it back. */
public final class CapturingCodeChannel implements CodeChannel {

    private final FactorType servesFactor;
    private final Map<String, String> lastCodeByTarget = new ConcurrentHashMap<>();

    public CapturingCodeChannel(FactorType servesFactor) {
        this.servesFactor = servesFactor;
    }

    @Override
    public FactorType servesFactor() {
        return servesFactor;
    }

    @Override
    public void sendCode(String target, String code) {
        lastCodeByTarget.put(target, code);
    }

    public String lastCodeFor(String target) {
        return lastCodeByTarget.get(target);
    }
}
