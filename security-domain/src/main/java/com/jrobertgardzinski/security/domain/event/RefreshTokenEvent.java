package com.jrobertgardzinski.security.domain.event;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;

// todo stop using toString.
public sealed interface RefreshTokenEvent {
    record Passed(SessionTokens sessionTokens) implements RefreshTokenEvent {}
    record Expired(Email email) implements RefreshTokenEvent {
        @Override
        public String toString() {
            return String.format("Refresh Token for %s has expired", email);
        }
    }
    record NotFound(Email email) implements RefreshTokenEvent {
        @Override
        public String toString() {
            return "No refresh token found for " + email.value();
        }
    }
}
