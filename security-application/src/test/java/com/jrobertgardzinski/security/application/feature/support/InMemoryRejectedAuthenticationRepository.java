package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryRejectedAuthenticationRepository implements RejectedAuthenticationRepository {

    private final List<RejectedAuthentication> records = new ArrayList<>();
    private long sequence = 0;

    @Override
    public RejectedAuthentication create(RejectedAuthenticationDetails details) {
        RejectedAuthentication record = new RejectedAuthentication(details, new RejectedAuthenticationId(++sequence));
        records.add(record);
        return record;
    }

    @Override
    public FailuresCount countFailuresBy(IpAddress ipAddress, LocalDateTime since) {
        long count = records.stream()
                .map(RejectedAuthentication::details)
                .filter(details -> details.ipAddress().equals(ipAddress) && details.time().isAfter(since))
                .count();
        return new FailuresCount((int) count);
    }

    @Override
    public void removeAllFor(IpAddress ipAddress) {
        records.removeIf(record -> record.details().ipAddress().equals(ipAddress));
    }
}
