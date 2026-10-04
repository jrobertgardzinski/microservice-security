package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules the domain enforces on a session's tokens: a validity of zero reaches a token's
 * expiry, and nothing was asking. Cut out of {@code AddressAndTokenRulesTest} when the domain split
 * by area.
 */
@Epic("Domain")
@Feature("Value rules")
class SessionTokenRulesTest {

    @Test
    @DisplayName("a minted token always carries something")
    void a_minted_token_is_never_blank() {
        assertThat(AccessToken.random().value()).isNotBlank();
    }

    @Test
    @DisplayName("a validity of less than an hour is refused, whichever token it is for")
    void validity_has_a_floor() {
        assertThatCode(() -> new AccessTokenValidityInHours(1)).doesNotThrowAnyException();
        assertThatCode(() -> new RefreshTokenValidityInHours(24)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new AccessTokenValidityInHours(0))
                .as("a token valid for zero hours is one nobody can use, minted on every sign-in")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenValidityInHours(-1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(AccessTokenValidityInHours.DEFAULT.value())
                .as("the code default must itself satisfy the rule it ships with")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("each kind of token has its own ceiling, and they are not the same number")
    void validity_has_a_ceiling_per_kind() {
        assertThatCode(() -> new AccessTokenValidityInHours(AccessTokenValidityInHours.MAX))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new AccessTokenValidityInHours(AccessTokenValidityInHours.MAX + 1))
                .as("an access token cannot be withdrawn before it expires — offline verifiers"
                        + " hold it against the JWK set and never ask again — so past a day it"
                        + " is a password with an expiry date, not a session token")
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> new RefreshTokenValidityInHours(24 * 30))
                .as("a month of 'stay signed in on my phone' is ordinary for the revocable one")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new RefreshTokenValidityInHours(RefreshTokenValidityInHours.MAX + 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(RefreshTokenValidityInHours.MAX)
                .as("there used to be no ceiling at all: 87600 hours — a decade — came from one"
                        + " typo in a property and nothing noticed")
                .isGreaterThan(AccessTokenValidityInHours.MAX);
    }
}
