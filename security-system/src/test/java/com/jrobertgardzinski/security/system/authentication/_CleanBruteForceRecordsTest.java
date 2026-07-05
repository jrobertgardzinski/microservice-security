package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
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
        cleanBruteForceRecords = new _CleanBruteForceRecords(
                rejectedAuthenticationRepository, authenticationBlockRepository);
    }

    @Example
    @Label("Removes both failed-authentication records and authentication blocks for the IP")
    void removes_all_records_for_ip() {
        cleanBruteForceRecords.execute(IP);

        assertAll(
                () -> Mockito.verify(rejectedAuthenticationRepository).removeAllFor(IP),
                () -> Mockito.verify(authenticationBlockRepository).removeAllFor(IP)
        );
    }
}
