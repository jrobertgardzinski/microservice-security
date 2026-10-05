package com.jrobertgardzinski.security.application.core;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.core.User;
import com.jrobertgardzinski.security.domain.mailbox.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.core.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.mailbox.FakeEmailVerificationRepository;
import com.jrobertgardzinski.security.domain.core.FakeUserRepository;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.system.core.Register;
import com.jrobertgardzinski.security.system.core.SourceThrottle;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationServiceTest {

    private static final String ADDRESS = "user@example.com";
    private static final String STRONG = "StrongPassword1!";
    private static final IpAddress SOURCE = new IpAddress("203.0.113.7");

    private final Clock clock = Clock.systemUTC();
    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeEmailVerificationRepository verifications = new FakeEmailVerificationRepository(clock);
    private final EmailVerificationNotifier links = Mockito.mock(EmailVerificationNotifier.class);
    private final RegistrationNoticeNotifier notices = Mockito.mock(RegistrationNoticeNotifier.class);
    private final TransactionBoundary inline = new TransactionBoundary() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private RegistrationService service;

    @BeforeEach
    void init() {
        service = serviceAllowing(5);
    }

    private RegistrationService serviceAllowing(int perWindow) {
        Register register = new Register(users, new CanRegisterConfig(), new FakeHashAlgorithm(),
                PasswordPolicy::withDefaults);
        return new RegistrationService(register, new RequestEmailVerification(verifications, links),
                verifications, notices, new SourceThrottle(perWindow, Duration.ofMinutes(15), clock), inline);
    }

    @Test
    void a_fresh_address_is_registered_and_sent_a_link() {
        assertInstanceOf(RegistrationService.Outcome.Registered.class, service.register(ADDRESS, STRONG, SOURCE));

        Mockito.verify(links).sendVerificationLink(Mockito.eq(Email.of(ADDRESS)), Mockito.any());
        Mockito.verifyNoInteractions(notices);
    }

    @Test
    void a_verified_address_is_quietly_refused_and_its_owner_told() {
        users.save(new User(Email.of(ADDRESS), new HashedPassword("seed-hash")));
        verifications.markVerified(Email.of(ADDRESS));

        assertInstanceOf(RegistrationService.Outcome.QuietlyRefused.class, service.register(ADDRESS, STRONG, SOURCE));

        Mockito.verify(notices).sendAlreadyRegistered(Email.of(ADDRESS));
        Mockito.verifyNoInteractions(links);
    }

    @Test
    void an_unverified_address_is_quietly_refused_and_sent_a_fresh_link() {
        users.save(new User(Email.of(ADDRESS), new HashedPassword("seed-hash")));

        assertInstanceOf(RegistrationService.Outcome.QuietlyRefused.class, service.register(ADDRESS, STRONG, SOURCE));

        Mockito.verify(links).sendVerificationLink(Mockito.eq(Email.of(ADDRESS)), Mockito.any());
        Mockito.verifyNoInteractions(notices);
    }

    @Test
    void an_address_the_domain_cannot_read_is_rejected_not_thrown() {
        RegistrationService.Outcome.Rejected rejected = assertInstanceOf(RegistrationService.Outcome.Rejected.class,
                service.register("not-an-address", STRONG, SOURCE));

        assertFalse(rejected.emailErrors().codes().isEmpty());
        Mockito.verifyNoInteractions(links, notices);
    }

    @Test
    void a_source_over_its_budget_is_throttled_before_anything_is_written() {
        service = serviceAllowing(1);
        service.register("first@example.com", STRONG, SOURCE);

        RegistrationService.Outcome.Throttled throttled = assertInstanceOf(RegistrationService.Outcome.Throttled.class,
                service.register(ADDRESS, STRONG, SOURCE));

        assertTrue(throttled.retryAfterSeconds() > 0);
        assertFalse(users.existsBy(NormalizedEmail.of(Email.of(ADDRESS))));
    }
}
