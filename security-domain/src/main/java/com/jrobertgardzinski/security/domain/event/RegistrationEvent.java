package com.jrobertgardzinski.security.domain.event;

import com.jrobertgardzinski.email.domain.Email;

public sealed interface RegistrationEvent {
    record RegistrationPassedEvent(Email email) implements RegistrationEvent {
    }

    sealed interface RegistrationFailedEvent extends RegistrationEvent permits UserAlreadyExistsEvent {
        String error(Email email);
    }
    record UserAlreadyExistsEvent() implements RegistrationFailedEvent {
        @Override
        public String error(Email email) {
            return String.format("User with the e-mail: %s already exists!", email.value());
        }
    }
}
