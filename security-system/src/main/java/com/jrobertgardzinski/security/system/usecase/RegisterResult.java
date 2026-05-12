package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.event.registration.RegistrationEvent;

import java.util.List;

public sealed interface RegisterResult {

    record Valid(RegistrationEvent event) implements RegisterResult {}

    record Invalid(List<String> emailErrors, List<String> passwordErrors) implements RegisterResult {}
}
