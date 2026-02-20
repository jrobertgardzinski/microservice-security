package com.jrobertgardzinski.token.config;

/**
 * Immutable configuration for token lifetimes.
 * All defaults live in the Builder — one place to change them.
 */
public record SessionConfig(int refreshTokenValidityHours, int accessTokenValidityHours) {

    public SessionConfig {
        if (refreshTokenValidityHours < 1) {
            throw new IllegalArgumentException("refreshTokenValidityHours must be at least 1");
        }
        if (accessTokenValidityHours < 1) {
            throw new IllegalArgumentException("accessTokenValidityHours must be at least 1");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int refreshTokenValidityHours = 48;
        private int accessTokenValidityHours = 48;

        public Builder refreshTokenValidityHours(int refreshTokenValidityHours) {
            this.refreshTokenValidityHours = refreshTokenValidityHours;
            return this;
        }

        public Builder accessTokenValidityHours(int accessTokenValidityHours) {
            this.accessTokenValidityHours = accessTokenValidityHours;
            return this;
        }

        public SessionConfig build() {
            return new SessionConfig(refreshTokenValidityHours, accessTokenValidityHours);
        }
    }
}
