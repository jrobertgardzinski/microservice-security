package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.vo.OtpCode;

public interface OtpCodeGenerator {

    OtpCode generate();
}
