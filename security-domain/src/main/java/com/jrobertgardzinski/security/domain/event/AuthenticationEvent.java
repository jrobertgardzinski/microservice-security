package com.jrobertgardzinski.security.domain.event;

import com.jrobertgardzinski.email.domain.Email;

public sealed interface AuthenticationEvent {
    record Passed(Email email) implements AuthenticationEvent { }

    // todo bring "permits" back whenever you are ready to introduce security notifications
    record Failed(Email email) implements AuthenticationEvent /*permits UserNotFoundEvent, WrongPasswordEvent*/ {}

}
