package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A blank token reaches a lookup, and nothing was asking. Cut out of
 * {@code AddressAndTokenRulesTest} when the domain split by area.
 */
@Epic("Domain")
@Feature("Value rules")
class MailboxTokenRulesTest {

    @Test
    @DisplayName("a token that carries nothing is not a token")
    void a_blank_token_is_refused() {
        assertThatThrownBy(() -> new VerificationToken(null))
                .as("an omitted field used to reach String.isBlank() and escape as a 500")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificationToken(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificationToken("   "))
                .as("whitespace is not a secret, and would match nothing but itself")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
