package com.jrobertgardzinski.security.domain.config;

public record SaltConfig(int byteLength) {
    public SaltConfig {
        if (byteLength < 8) throw new IllegalArgumentException("Salt byte length must be at least 8");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int byteLength = 16;

        public Builder byteLength(int byteLength) {
            this.byteLength = byteLength;
            return this;
        }

        public SaltConfig build() {
            return new SaltConfig(byteLength);
        }
    }
}
