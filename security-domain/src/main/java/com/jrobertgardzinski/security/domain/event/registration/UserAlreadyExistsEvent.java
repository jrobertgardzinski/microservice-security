package com.jrobertgardzinski.security.domain.event.registration;

import com.jrobertgardzinski.email.domain.Email;

public record UserAlreadyExistsEvent() implements RegistrationFailedEvent {
    @Override
    public String error(Email email) {
        return String.format("User with the e-mail: %s already exists!", email.value());
    }
}
