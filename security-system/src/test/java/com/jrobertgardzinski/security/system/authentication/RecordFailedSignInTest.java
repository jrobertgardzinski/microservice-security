package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.domain.core.AttemptedAccount;
import com.jrobertgardzinski.security.domain.core.LockoutSubject;
import com.jrobertgardzinski.security.domain.core.Source;
import com.jrobertgardzinski.email.domain.Email;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Epic("Use case")
@Feature("Authentication")
@Story("Update brute-force records")
class RecordFailedSignInTest {

    private static final Source IP = Source.of(new IpAddress("192.168.0.1"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private RecordFailedSignIn recordFailedSignIn;

    @BeforeTry
    void init() {
        rejectedAuthenticationRepository = Mockito.mock(RejectedAuthenticationRepository.class);
        recordFailedSignIn = new RecordFailedSignIn(rejectedAuthenticationRepository, CLOCK);
    }

    @Example
    @Label("Records a failed authentication for the IP stamped with the current time")
    void records_failed_authentication() {
        LockoutSubject subject = new LockoutSubject(IP, AttemptedAccount.of(Email.of("victim@example.com")));

        recordFailedSignIn.record(subject);

        Mockito.verify(rejectedAuthenticationRepository)
                .create(new RejectedAuthenticationDetails(subject, LocalDateTime.now(CLOCK)));
    }
}
