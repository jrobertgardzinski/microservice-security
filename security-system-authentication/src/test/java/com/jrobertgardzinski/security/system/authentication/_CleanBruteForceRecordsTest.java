package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.AttemptedAccount;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.email.domain.Email;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;

@Epic("Use case")
@Feature("Authentication")
@Story("Clean brute-force records")
class _CleanBruteForceRecordsTest {

    private static final Source IP = Source.of(new IpAddress("192.168.0.1"));

    private RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private AuthenticationBlockRepository authenticationBlockRepository;
    private _CleanBruteForceRecords cleanBruteForceRecords;

    @BeforeTry
    void init() {
        rejectedAuthenticationRepository = Mockito.mock(RejectedAuthenticationRepository.class);
        authenticationBlockRepository = Mockito.mock(AuthenticationBlockRepository.class);
        cleanBruteForceRecords = new _CleanBruteForceRecords(rejectedAuthenticationRepository);
    }

    @Example
    @Label("Forgets THIS pair's failures — and leaves a placed block standing")
    void forgets_only_this_pairs_failures() {
        LockoutSubject subject = new LockoutSubject(IP, AttemptedAccount.of(Email.of("owner@example.com")));

        cleanBruteForceRecords.execute(subject);

        assertAll(
                () -> Mockito.verify(rejectedAuthenticationRepository).removeAllFor(subject),
                // clearing the BLOCK here is what once turned one known-good password into an
                // amnesty for everything the address had been trying
                () -> Mockito.verifyNoInteractions(authenticationBlockRepository)
        );
    }
}
