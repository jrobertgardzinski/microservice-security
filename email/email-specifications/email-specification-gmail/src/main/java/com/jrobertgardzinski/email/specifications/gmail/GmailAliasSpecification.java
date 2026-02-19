package com.jrobertgardzinski.email.specifications.gmail;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

public class GmailAliasSpecification implements EmailPolicy {

    @Override
    public boolean isSatisfiedBy(Email email) {
        return isGmail(email);
    }

    public Email normalise(Email email) {
        if (!isGmail(email)) {
            return email;
        }
        String canonical = email.local().value().replaceAll("\\+.*", "").replace(".", "") + "@gmail.com";
        return Email.of(canonical);
    }

    private boolean isGmail(Email email) {
        String domain = email.domain().value();
        return domain.equals("gmail.com") || domain.equals("googlemail.com");
    }
}
