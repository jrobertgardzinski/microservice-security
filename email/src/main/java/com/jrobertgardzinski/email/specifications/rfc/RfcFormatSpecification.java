package com.jrobertgardzinski.email.specifications.rfc;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailPolicy;

import java.util.regex.Pattern;

public class RfcFormatSpecification implements EmailPolicy {

    private static final Pattern RFC_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+" +
            "@" +
            "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
            "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );

    @Override
    public boolean isSatisfiedBy(Email email) {
        return RFC_PATTERN.matcher(email.value()).matches();
    }
}
