package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.repository.OtpCodeHasher;
import com.jrobertgardzinski.security.domain.vo.HashedOtpCode;
import com.jrobertgardzinski.security.domain.vo.OtpCode;

/**
 * Reversible "hash" for testing — production uses something irreversible.
 * Good enough to verify the use cases store hashes and compare correctly.
 */
public final class StubOtpCodeHasher implements OtpCodeHasher {

    @Override
    public HashedOtpCode hash(OtpCode code) {
        return new HashedOtpCode("hash:" + code.value());
    }

    @Override
    public boolean verify(HashedOtpCode hashedCode, OtpCode code) {
        return hashedCode.value().equals("hash:" + code.value());
    }
}
