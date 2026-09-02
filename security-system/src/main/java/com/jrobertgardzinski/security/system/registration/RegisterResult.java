package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    /**
     * Both channels answer in one shape: the codes of the rules that failed, next to the policy
     * that was in force for this very attempt — so whoever renders the refusal can say not only
     * WHICH rule broke but against WHAT (the minimum length, the accepted special characters, the
     * company domains an employee may register from).
     */
    record Rejected(EmailErrorCodes emailErrors, CanRegisterConfig emailPolicy,
                    PasswordErrorCodes passwordErrors, PasswordPolicy passwordPolicy) implements RegisterResult {}

    record EmailAlreadyTaken(Email email) implements RegisterResult {}
}
