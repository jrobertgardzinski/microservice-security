package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;

import java.time.LocalDateTime;

/**
 * Details of a {@link RejectedAuthentication}. Carries the whole {@link LockoutSubject} — the PAIR
 * (source, attempted account) is what counts towards a lockout; inside the source the observed
 * user-agent stays for forensics and keys nothing, because it rotates for free.
 */
public record RejectedAuthenticationDetails(LockoutSubject subject, LocalDateTime time) {

    /** The knocking side — adapters still store it column by column. */
    public Source source() {
        return subject.source();
    }

    /** The side that was knocked on. */
    public AttemptedAccount account() {
        return subject.account();
    }
}
