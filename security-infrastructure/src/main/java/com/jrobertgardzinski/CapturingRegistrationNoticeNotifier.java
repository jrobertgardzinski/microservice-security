package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.RegistrationNoticeNotifier;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test notifier ({@code test} environment only): instead of mailing the address owner it records
 * which addresses were told "you already have an account", so a black-box test can assert the
 * quiet-refusal side channel without an SMTP server.
 */
@Singleton
@Requires(env = "test")
public final class CapturingRegistrationNoticeNotifier implements RegistrationNoticeNotifier {

    private final List<String> noticedEmails = new CopyOnWriteArrayList<>();

    @Override
    public void sendAlreadyRegistered(Email email) {
        noticedEmails.add(email.value());
    }

    public List<String> noticedEmails() {
        return List.copyOf(noticedEmails);
    }
}
