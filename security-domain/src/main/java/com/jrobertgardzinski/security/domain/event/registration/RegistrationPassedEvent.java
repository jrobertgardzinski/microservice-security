package com.jrobertgardzinski.security.domain.event.registration;

import com.jrobertgardzinski.email.domain.Email;

public record RegistrationPassedEvent(Email email) implements RegistrationEvent {
}
