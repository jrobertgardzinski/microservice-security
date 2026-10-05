package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.vo.FactorType;

/**
 * A human-readable label for a factor type, for the enrolment list and the sign-in prompt. Unknown
 * types fall back to their id — a new factor works without a label, it just reads less prettily.
 */
final class FactorLabels {

    private FactorLabels() {
    }

    static String of(FactorType type) {
        if (FactorType.EMAIL_CODE.equals(type)) {
            return "e-mail code";
        }
        if (FactorType.SMS_CODE.equals(type)) {
            return "SMS code";
        }
        if (FactorType.TOTP.equals(type)) {
            return "authenticator app";
        }
        if (FactorType.WEBAUTHN.equals(type)) {
            return "passkey";
        }
        return type.value();
    }
}
