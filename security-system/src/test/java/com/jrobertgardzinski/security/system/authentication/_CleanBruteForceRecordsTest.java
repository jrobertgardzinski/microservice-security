package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.testkit.Concept;
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
@Concept("brute-force-guard")
class _CleanBruteForceRecordsTest {

    private static final IpAddress IP = new IpAddress("192.168.0.1");

    private FailedAuthenticationRepository failedAuthenticationRepository;
    private AuthenticationBlockRepository authenticationBlockRepository;
    private _CleanBruteForceRecords cleanBruteForceRecords;

    @BeforeTry
    void init() {
        failedAuthenticationRepository = Mockito.mock(FailedAuthenticationRepository.class);
        authenticationBlockRepository = Mockito.mock(AuthenticationBlockRepository.class);
        cleanBruteForceRecords = new _CleanBruteForceRecords(
                failedAuthenticationRepository, authenticationBlockRepository);
    }

    @Example
    @Label("Removes both failed-authentication records and authentication blocks for the IP")
    void removes_all_records_for_ip() {
        cleanBruteForceRecords.execute(IP);

        assertAll(
                () -> Mockito.verify(failedAuthenticationRepository).removeAllFor(IP),
                () -> Mockito.verify(authenticationBlockRepository).removeAllFor(IP)
        );
    }
}
