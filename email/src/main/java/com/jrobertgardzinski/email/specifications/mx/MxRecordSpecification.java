package com.jrobertgardzinski.email.specifications.mx;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

public class MxRecordSpecification implements EmailPolicy {

    private final MxRecordPort mxRecordPort;

    public MxRecordSpecification(MxRecordPort mxRecordPort) {
        this.mxRecordPort = mxRecordPort;
    }

    @Override
    public boolean isSatisfiedBy(Email email) {
        return mxRecordPort.hasMxRecord(email);
    }
}
