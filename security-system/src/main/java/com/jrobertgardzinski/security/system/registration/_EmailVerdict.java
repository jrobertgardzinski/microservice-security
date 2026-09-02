package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.Optional;
import java.util.function.Supplier;

record _EmailVerdict(Outcome<Email> outcome, CanRegisterConfig policy) {

    static _EmailVerdict judge(CanRegisterConfig policy, Supplier<Email> candidate) {
        CanRegister canRegister = CanRegister.builder()
                .blockingDomains(policy.blockedDomains())
                .blockingDisposable(policy.disposableDomains())
                .requiringCompanyEmployee(policy.companyDomains())
                .build();
        return new _EmailVerdict(canRegister.evaluate(candidate), policy);
    }

    Optional<Email> accepted() {
        return outcome.findValue();
    }

    EmailErrorCodes errorCodes() {
        return EmailErrorCodes.of(outcome);
    }
}
