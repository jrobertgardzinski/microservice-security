package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.event.registration.RegistrationEvent;
import com.jrobertgardzinski.security.system.factory.UserRegistrationValidationException;

public sealed interface RegisterResult {

    record Valid(RegistrationEvent event) implements RegisterResult {}

    record Invalid(UserRegistrationValidationException exception) implements RegisterResult {}
}
