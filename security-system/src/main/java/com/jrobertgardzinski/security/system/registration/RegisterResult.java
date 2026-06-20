package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.security.domain.entity.User;

import java.util.List;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    // todo consider dedicated errors for email and password
    record Rejected(List<String> emailErrors, List<String> passwordErrors) implements RegisterResult {}
}
