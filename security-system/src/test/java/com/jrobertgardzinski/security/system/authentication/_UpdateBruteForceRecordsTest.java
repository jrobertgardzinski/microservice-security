package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.testkit.Concept;
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
@Concept("brute-force-guard")
class _UpdateBruteForceRecordsTest {

    private static final IpAddress IP = new IpAddress("192.168.0.1");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private FailedAuthenticationRepository failedAuthenticationRepository;
    private _UpdateBruteForceRecords updateBruteForceRecords;

    @BeforeTry
    void init() {
        failedAuthenticationRepository = Mockito.mock(FailedAuthenticationRepository.class);
        updateBruteForceRecords = new _UpdateBruteForceRecords(failedAuthenticationRepository, CLOCK);
    }

    @Example
    @Label("Records a failed authentication for the IP stamped with the current time")
    void records_failed_authentication() {
        updateBruteForceRecords.execute(IP);

        Mockito.verify(failedAuthenticationRepository)
                .create(new FailedAuthenticationDetails(IP, LocalDateTime.now(CLOCK)));
    }
}
