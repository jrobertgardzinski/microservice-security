package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;

/**
 * How a person is shown to other people. Today: the address with its local part masked to one
 * character, the same shape the portal has always rendered; the day accounts get a chosen name,
 * this is the one place that changes.
 */
public record DisplayName(String value) {

    public static DisplayName of(Email email) {
        String address = email.value();
        return new DisplayName(address.charAt(0) + "***" + address.substring(address.indexOf('@')));
    }
}
