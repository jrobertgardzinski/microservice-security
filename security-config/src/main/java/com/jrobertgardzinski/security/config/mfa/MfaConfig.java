package com.jrobertgardzinski.security.config.mfa;

import com.jrobertgardzinski.security.config.mfa.vo.AuthSessionExpiryMinutes;
import com.jrobertgardzinski.security.config.mfa.vo.OtpExpiryMinutes;

public record MfaConfig(AuthSessionExpiryMinutes authSessionExpiryMinutes,
                        OtpExpiryMinutes otpExpiryMinutes) {

    public MfaConfig {
        if (otpExpiryMinutes.value() > authSessionExpiryMinutes.value())
            throw new IllegalArgumentException("otpExpiryMinutes must be <= authSessionExpiryMinutes");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AuthSessionExpiryMinutes authSessionExpiryMinutes = AuthSessionExpiryMinutes.DEFAULT;
        private OtpExpiryMinutes otpExpiryMinutes = OtpExpiryMinutes.DEFAULT;

        public Builder authSessionExpiryMinutes(int value) {
            this.authSessionExpiryMinutes = new AuthSessionExpiryMinutes(value);
            return this;
        }

        public Builder otpExpiryMinutes(int value) {
            this.otpExpiryMinutes = new OtpExpiryMinutes(value);
            return this;
        }

        public MfaConfig build() {
            return new MfaConfig(authSessionExpiryMinutes, otpExpiryMinutes);
        }
    }
}
