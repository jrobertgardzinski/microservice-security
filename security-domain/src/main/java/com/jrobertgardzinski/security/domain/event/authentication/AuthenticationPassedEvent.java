package com.jrobertgardzinski.security.domain.event.authentication;

import com.jrobertgardzinski.email.domain.Email;

public record AuthenticationPassedEvent(Email email) implements AuthenticationEvent {
}
