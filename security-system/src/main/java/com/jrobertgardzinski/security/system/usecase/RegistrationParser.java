package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;
import com.jrobertgardzinski.util.constraint.Constraints;
import com.jrobertgardzinski.util.constraint.Decision;

import java.util.List;

/**
 * Parses raw registration input (email + password) into a {@link UserRegistration},
 * or reports per-field errors. Pure function — no exceptions across its boundary,
 * no state.
 */
public record RegistrationParser(Constraints<PlaintextPassword> passwordConstraints) {

    public Result parse(String emailRaw, String plaintextPassword) {
        Parsed<Email> email = parseEmail(emailRaw);
        Parsed<PlaintextPassword> password = parsePassword(plaintextPassword);

        if (email instanceof Parsed.Ok<Email> e && password instanceof Parsed.Ok<PlaintextPassword> p) {
            return new Result.Valid(new UserRegistration(e.value(), p.value()));
        }
        return new Result.Invalid(email.errors(), password.errors());
    }

    private static Parsed<Email> parseEmail(String raw) {
        try {
            return new Parsed.Ok<>(Email.of(raw));
        } catch (IllegalArgumentException e) {
            return new Parsed.Failed<>(List.of(e.getMessage()));
        }
    }

    private Parsed<PlaintextPassword> parsePassword(String raw) {
        try {
            PlaintextPassword p = PlaintextPassword.of(raw);
            return passwordConstraints.decide(p) instanceof Decision.Rejected r
                    ? new Parsed.Failed<>(r.errorCodes())
                    : new Parsed.Ok<>(p);
        } catch (IllegalArgumentException e) {
            return new Parsed.Failed<>(List.of(e.getMessage()));
        }
    }

    private sealed interface Parsed<T> {
        record Ok<T>(T value) implements Parsed<T> {}
        record Failed<T>(List<String> errors) implements Parsed<T> {}

        default List<String> errors() {
            return this instanceof Failed<?> f ? f.errors() : List.of();
        }
    }

    public sealed interface Result {
        record Valid(UserRegistration registration) implements Result {}
        record Invalid(List<String> emailErrors, List<String> passwordErrors) implements Result {}
    }
}
