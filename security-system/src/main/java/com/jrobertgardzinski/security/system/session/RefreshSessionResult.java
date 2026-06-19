package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;

// todo stop using toString.
public sealed interface RefreshSessionResult {
    record Refreshed(SessionTokens sessionTokens) implements RefreshSessionResult {}
    record Expired(Email email) implements RefreshSessionResult {
        @Override
        public String toString() {
            return String.format("Refresh Token for %s has expired", email);
        }
    }
    record NotFound(Email email) implements RefreshSessionResult {
        @Override
        public String toString() {
            return "No refresh token found for " + email.value();
        }
    }
}
