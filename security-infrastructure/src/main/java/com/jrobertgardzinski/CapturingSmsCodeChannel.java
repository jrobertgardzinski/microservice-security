package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test SMS {@link CodeChannel} ({@code test} environment only): captures the last code "sent" to
 * each number so a black-box test can read it back without an SMS gateway.
 */
@Singleton
@Requires(env = "test")
public final class CapturingSmsCodeChannel implements CodeChannel {

    private final Map<String, String> lastCodeByNumber = new ConcurrentHashMap<>();

    @Override
    public FactorType servesFactor() {
        return FactorType.SMS_CODE;
    }

    @Override
    public void sendCode(String target, String code) {
        lastCodeByNumber.put(target, code);
    }

    public String lastCodeFor(String number) {
        return lastCodeByNumber.get(number);
    }
}
