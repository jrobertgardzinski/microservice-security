package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.NormalizedEmail;

/**
 * The account someone tried to open — the other half of a failed sign-in, next to {@link Source},
 * which says who was knocking.
 *
 * <p>NORMALISED on purpose. Counting attempts against the raw address would hand an attacker a free
 * multiplier: {@code a.b@gmail.com} and {@code ab@gmail.com} reach the same mailbox but would land
 * in different buckets, so every dot and every {@code +tag} would buy another full allowance of
 * guesses. The normalisation rule already lives in the domain, so the type carries it rather than
 * hoping each adapter remembers.
 *
 * <p>An account that does not exist is still an attempted account. Spraying unknown addresses must
 * cost the same as spraying known ones, or the counter can be walked around by guessing wrong.
 */
public record AttemptedAccount(NormalizedEmail value) {

    public AttemptedAccount {
        if (value == null) {
            throw new IllegalArgumentException("an attempted account without an address is not an attempt");
        }
    }

    public static AttemptedAccount of(com.jrobertgardzinski.email.domain.Email email) {
        return new AttemptedAccount(NormalizedEmail.of(email));
    }
}
