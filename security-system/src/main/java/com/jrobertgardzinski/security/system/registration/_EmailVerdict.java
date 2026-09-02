package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The email half of one registration attempt: the outcome and the policy it was measured against,
 * kept together so a refusal can always say against WHAT the address was judged.
 */
record _EmailVerdict(Outcome<Email> outcome, CanRegisterConfig policy) {

    /** Judges the candidate against the policy in force; an absent domain list is an absent rule. */
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
