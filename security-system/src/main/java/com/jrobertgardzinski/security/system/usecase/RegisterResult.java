package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationEvent;
import com.jrobertgardzinski.util.constraint.Decision;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public sealed interface RegisterResult {

    record Valid(RegistrationEvent event) implements RegisterResult {}

    record Invalid(List<String> emailErrors, List<String> passwordErrors) implements RegisterResult {}

    static RegisterResult from(
            Decision<Email> emailDecision,
            Outcome<HashedPassword> passwordOutcome,
            Function<HashedPassword, RegistrationEvent> onValid) {

        List<String> emailErrors = emailDecision.errorCodes();
        List<String> passwordErrors = passwordOutcome.errorCodes();

        if (!emailErrors.isEmpty() || !passwordErrors.isEmpty()) {
            return new Invalid(emailErrors, passwordErrors);
        }
        return passwordOutcome.findValue()
                .map(onValid)
                .map(Valid::new)
                .orElseThrow();
    }
}
