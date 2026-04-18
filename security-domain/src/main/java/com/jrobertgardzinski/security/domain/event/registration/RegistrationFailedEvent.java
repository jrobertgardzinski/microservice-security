package com.jrobertgardzinski.security.domain.event.registration;

import com.jrobertgardzinski.email.domain.Email;

public sealed interface RegistrationFailedEvent extends RegistrationEvent permits PossibleRaceCondition, UserAlreadyExistsEvent {
    String error(Email email);
}
