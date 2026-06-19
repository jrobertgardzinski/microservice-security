package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.security.domain.entity.User;

import java.util.List;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    record Rejected(List<String> emailErrors, List<String> passwordErrors) implements RegisterResult {}
}
