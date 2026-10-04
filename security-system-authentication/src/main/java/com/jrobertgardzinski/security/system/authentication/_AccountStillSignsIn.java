package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * Asked again at the END of a factor chain: is this still an account that may be signed into?
 *
 * <p>Link #1 answered it, and then the chain took as long as the person took — a code typed from a
 * phone, a passkey fetched from another device, the five minutes a ticket is allowed to live. Every
 * question the password step asked can have changed its answer in that time: the account can have
 * been deleted, can have ASKED to be deleted (a request that starts a saga and must not hand out
 * fresh sessions while it runs), or can have moved to another address and left this one behind.
 *
 * <p>Nothing here is a new rule. It is the same three questions link #1 asks, asked once more where
 * the session is actually minted — because between them lies the only window in a sign-in that an
 * attacker does not have to hurry through.
 */
class _AccountStillSignsIn {

    private final UserRepository users;
    private final _RequireVerifiedEmail requireVerifiedEmail;

    _AccountStillSignsIn(UserRepository users, _RequireVerifiedEmail requireVerifiedEmail) {
        this.users = users;
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    boolean isTrueOf(Email email) {
        return users.findBy(email).isPresent()
                && !users.isPendingDeletion(email)
                && requireVerifiedEmail.isVerified(email);
    }
}
