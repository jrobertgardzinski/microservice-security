package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Starts an email change for a signed-in user: refuses if the new address is already taken,
 * otherwise mints a verification token, remembers the pending change, and e-mails the link to the
 * new address (ownership must be proven before the change takes effect).
 */
public class RequestEmailChange {

    private final UserRepository userRepository;
    private final EmailChangeRepository emailChangeRepository;
    private final EmailVerificationNotifier notifier;

    public RequestEmailChange(UserRepository userRepository, EmailChangeRepository emailChangeRepository,
                              EmailVerificationNotifier notifier) {
        this.userRepository = userRepository;
        this.emailChangeRepository = emailChangeRepository;
        this.notifier = notifier;
    }

    public RequestEmailChangeResult execute(Email currentEmail, Email newEmail) {
        if (userRepository.existsBy(NormalizedEmail.of(newEmail))) {
            return new RequestEmailChangeResult.EmailTaken();
        }
        VerificationToken token = VerificationToken.random();
        emailChangeRepository.startChange(new EmailChange(currentEmail, newEmail), token);
        notifier.sendVerificationLink(newEmail, token);
        return new RequestEmailChangeResult.Requested();
    }
}
