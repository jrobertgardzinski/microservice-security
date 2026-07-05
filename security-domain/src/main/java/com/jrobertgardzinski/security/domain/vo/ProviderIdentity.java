package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;

/**
 * What an external identity provider asserted about the caller, after the boundary has walked the
 * OAuth dance and validated the assertion. {@code subject} is the provider's own opaque id for
 * the person — the durable key of the link (an email can change on either side, the subject not).
 * {@code emailVerified} is the provider vouching that the inbox belongs to this person; without
 * it the assertion must not open or touch any account.
 */
public record ProviderIdentity(String provider, String subject, Email email, boolean emailVerified) {
}
