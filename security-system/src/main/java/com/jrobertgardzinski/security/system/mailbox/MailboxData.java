package com.jrobertgardzinski.security.system.mailbox;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.core.PersonalData;
import com.jrobertgardzinski.security.domain.mailbox.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.mailbox.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.mailbox.PasswordResetRepository;

/**
 * What the mailbox area keeps about a person: links mailed and not yet followed, and whether the
 * address was proven. None of it follows a person to a new address — a link mailed to the old
 * one, matched by address alone, would act on whoever holds that address next — so moving drops
 * it exactly as erasing does. The new address is proven by the change itself.
 */
public final class MailboxData implements PersonalData {

    private final PasswordResetRepository passwordResets;
    private final EmailChangeRepository emailChanges;
    private final EmailVerificationRepository emailVerifications;

    public MailboxData(PasswordResetRepository passwordResets, EmailChangeRepository emailChanges,
                       EmailVerificationRepository emailVerifications) {
        this.passwordResets = passwordResets;
        this.emailChanges = emailChanges;
        this.emailVerifications = emailVerifications;
    }

    @Override
    public void erase(Email email) {
        passwordResets.purge(email);
        emailChanges.purge(email);
        emailVerifications.purge(email);
    }

    @Override
    public void move(Email from, Email to) {
        erase(from);
    }
}
