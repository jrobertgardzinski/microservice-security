package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two rules about the mailbox tokens that only ever get broken by accident.
 */
@Epic("Domain")
@Feature("Tokens")
class MailboxTokenSecrecyTest {

    private static final String SECRET = "a-live-credential";

    @Test
    @DisplayName("a token never prints its own secret")
    void the_secret_does_not_travel_through_toString() {
        for (Object token : new Object[]{new VerificationToken(SECRET), new PasswordResetToken(SECRET)}) {
            assertFalse(token.toString().contains(SECRET),
                    token.getClass().getSimpleName() + " put a live credential into a string —"
                            + " the first log line that interpolates one carries it into a system"
                            + " with its own retention");
            assertTrue(token.toString().contains(token.getClass().getSimpleName()),
                    "and it should still say what it is: " + token);
        }
    }

    @Test
    @DisplayName("two different kinds of token are not the same token")
    void equality_is_by_kind_as_well_as_value() {
        assertEquals(new VerificationToken(SECRET), new VerificationToken(SECRET));
        assertNotEquals(new VerificationToken(SECRET), new PasswordResetToken(SECRET));
        assertNotEquals(new VerificationToken(SECRET).hashCode(), new PasswordResetToken(SECRET).hashCode(),
                "and they must not collide in a map either");
    }
}
