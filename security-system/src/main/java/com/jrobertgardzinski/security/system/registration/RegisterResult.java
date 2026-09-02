package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    record Rejected(EmailErrorCodes emailErrors, CanRegisterConfig emailPolicy,
                    PasswordErrorCodes passwordErrors, PasswordPolicy passwordPolicy) implements RegisterResult {}

    record EmailAlreadyTaken(Email email) implements RegisterResult {}
}
