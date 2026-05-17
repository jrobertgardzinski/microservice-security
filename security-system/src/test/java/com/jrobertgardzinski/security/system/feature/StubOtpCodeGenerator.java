package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.repository.OtpCodeGenerator;
import com.jrobertgardzinski.security.domain.vo.OtpCode;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Generator returning predetermined OTP codes in order — useful for asserting
 * what the production code did with the code it received.
 */
public final class StubOtpCodeGenerator implements OtpCodeGenerator {

    private final Deque<String> upcoming = new ArrayDeque<>();

    public StubOtpCodeGenerator(String... codes) {
        for (String c : codes) upcoming.add(c);
    }

    @Override
    public OtpCode generate() {
        if (upcoming.isEmpty()) throw new IllegalStateException("no more stub OTPs queued");
        return new OtpCode(upcoming.poll());
    }
}
