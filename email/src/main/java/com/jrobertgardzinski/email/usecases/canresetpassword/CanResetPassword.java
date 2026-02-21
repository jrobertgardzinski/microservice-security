package com.jrobertgardzinski.email.usecases.canresetpassword;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;
import com.jrobertgardzinski.email.specifications.rfc.RfcFormatSpecification;

public class CanResetPassword implements EmailPolicy {

    private final EmailPolicy policy = new RfcFormatSpecification();

    @Override
    public boolean isSatisfiedBy(Email email) {
        return policy.isSatisfiedBy(email);
    }
}
