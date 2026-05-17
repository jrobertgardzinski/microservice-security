package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailOtpSenderPort;
import com.jrobertgardzinski.security.domain.vo.OtpCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every send invocation so tests can assert that the right code went
 * to the right recipient — without an actual mail transport.
 */
public final class CapturingEmailOtpSender implements EmailOtpSenderPort {

    public record Sent(Email recipient, OtpCode code) {}

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void send(Email recipient, OtpCode code) {
        sent.add(new Sent(recipient, code));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public Sent last() {
        return sent.get(sent.size() - 1);
    }

    public int count() {
        return sent.size();
    }
}
