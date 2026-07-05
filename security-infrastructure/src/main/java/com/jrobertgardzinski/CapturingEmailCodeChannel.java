package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test e-mail {@link CodeChannel} ({@code test} environment only): instead of mailing the code it
 * captures the last one sent per target, so a black-box test — in-process or the Angular/React e2e
 * via {@code /test/mailbox} — can read back the code it would have received. Never active outside
 * the {@code test} environment.
 */
@Singleton
@Requires(env = "test")
public final class CapturingEmailCodeChannel implements CodeChannel {

    private final Map<String, String> lastCodeByTarget = new ConcurrentHashMap<>();

    @Override
    public FactorType servesFactor() {
        return FactorType.EMAIL_CODE;
    }

    @Override
    public void sendCode(String target, String code) {
        lastCodeByTarget.put(target, code);
    }

    public String lastCodeFor(String target) {
        return lastCodeByTarget.get(target);
    }
}
