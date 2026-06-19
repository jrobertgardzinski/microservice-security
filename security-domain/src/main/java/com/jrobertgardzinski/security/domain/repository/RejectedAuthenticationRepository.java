package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.LocalDateTime;

public interface RejectedAuthenticationRepository {
    RejectedAuthentication create(RejectedAuthenticationDetails value);
    FailuresCount countFailuresBy(IpAddress ipAddress, LocalDateTime since);
    void removeAllFor(IpAddress ipAddress);
}
