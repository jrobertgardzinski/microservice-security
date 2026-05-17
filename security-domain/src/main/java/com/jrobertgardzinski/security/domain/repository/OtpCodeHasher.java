package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.vo.HashedOtpCode;
import com.jrobertgardzinski.security.domain.vo.OtpCode;

public interface OtpCodeHasher {

    HashedOtpCode hash(OtpCode code);

    boolean verify(HashedOtpCode hashedCode, OtpCode code);
}
