package com.jrobertgardzinski.password.policy;

/**
 * Immutable configuration record for password policy.
 * All defaults live in the Builder — one place to change them.
 */
public record PasswordPolicyConfig(
        int minLength,
        boolean requireLowercase,
        boolean requireUppercase,
        boolean requireDigit,
        String specialChars
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int minLength = 12;
        private boolean requireLowercase = true;
        private boolean requireUppercase = true;
        private boolean requireDigit = true;
        private String specialChars = "#?!";

        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder requireLowercase(boolean requireLowercase) {
            this.requireLowercase = requireLowercase;
            return this;
        }

        public Builder requireUppercase(boolean requireUppercase) {
            this.requireUppercase = requireUppercase;
            return this;
        }

        public Builder requireDigit(boolean requireDigit) {
            this.requireDigit = requireDigit;
            return this;
        }

        public Builder specialChars(String specialChars) {
            this.specialChars = specialChars;
            return this;
        }

        public Builder noSpecialChars() {
            this.specialChars = "";
            return this;
        }

        public PasswordPolicyConfig build() {
            return new PasswordPolicyConfig(minLength, requireLowercase, requireUppercase, requireDigit, specialChars);
        }
    }
}
