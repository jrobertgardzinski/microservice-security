package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;

/**
 * The escape valve for the person who mistyped their own password and then got it right — narrowed
 * to the pair that earned it.
 *
 * <p>It used to clear the whole SOURCE, blocks included, and that made one known-good credential an
 * amnesty for everything that address had been trying: an attacker with an account of their own
 * signed into it and the victim's counter went back to zero. Removing the clearing altogether
 * closed that hole and opened another — an office, a CGNAT or a CI runner blocked for everyone over
 * three of somebody's typos.
 *
 * <p>So: this pair's failures go, and NOTHING else does. The address's other records are other
 * people's business, and a placed BLOCK survives on its own timer — clearing it here would hand the
 * amnesty straight back.
 */
class _CleanBruteForceRecords {

    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;

    public _CleanBruteForceRecords(RejectedAuthenticationRepository rejectedAuthenticationRepository) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
    }

    public void execute(LockoutSubject subject) {
        rejectedAuthenticationRepository.removeAllFor(subject);
    }
}
