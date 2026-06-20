package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.security.domain.entity.User;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    record Rejected(EmailErrorCodes emailErrors, PasswordErrorCodes passwordErrors) implements RegisterResult {}
}
