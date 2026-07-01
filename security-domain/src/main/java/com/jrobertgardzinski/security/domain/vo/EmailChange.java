package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;

/**
 * A requested email change awaiting confirmation: from the user's current address to the new one.
 */
public record EmailChange(Email currentEmail, Email newEmail) {
}
