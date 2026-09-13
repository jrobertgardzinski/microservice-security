package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules the domain enforces on the values it is handed, and which nothing was asking about.
 *
 * <p>Every one of these is a guard that only ever fails silently: an address the validator lets
 * through reaches a {@code VARCHAR} column, a blank token reaches a lookup, a validity of zero
 * reaches a token's expiry. They are cheap to keep honest and expensive to discover broken.
 */
@Epic("Domain")
@Feature("Value rules")
class AddressAndTokenRulesTest {

    @Nested
    @DisplayName("IpAddress")
    class Addresses {

        @Test
        @DisplayName("a dotted quad is admitted only when every octet is one")
        void ipv4_octets() {
            assertThatCode(() -> new IpAddress("203.0.113.7")).doesNotThrowAnyException();
            assertThatCode(() -> new IpAddress("0.0.0.0")).doesNotThrowAnyException();
            assertThatCode(() -> new IpAddress("255.255.255.255")).doesNotThrowAnyException();

            assertThatThrownBy(() -> new IpAddress("256.0.113.7"))
                    .as("an octet over 255 is not an address, whatever it parses as")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("203.0.113"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("203.0.113.7.8"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("203.0.113.007"))
                    .as("a leading zero is an octal invitation: 007 and 7 must not both be this host")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress(" 203.0.113.7"))
                    .as("surrounding space is not trimmed away into a valid address")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("IPv6 is admitted with one compression at most")
        void ipv6_compression() {
            assertThatCode(() -> new IpAddress("2001:db8:0:0:0:0:0:1")).doesNotThrowAnyException();
            assertThatCode(() -> new IpAddress("2001:db8::1")).doesNotThrowAnyException();
            assertThatCode(() -> new IpAddress("::1")).doesNotThrowAnyException();
            assertThatCode(() -> new IpAddress("::ffff:203.0.113.7"))
                    .as("the IPv4-mapped form is how a v4 client reaches a dual-stack proxy")
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> new IpAddress("2001::db8::1"))
                    .as("two compressions do not name one address — the middle is ambiguous")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("2001:db8:0:0:0:0:0:1:2"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("2001:db8:0:0:0:0:0:zzzz"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("2001:db8"))
                    .as("a bare prefix with no compression is not a whole address")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * The zone id and the prefix pass the domain, which is why {@code ClientIpResolver}
         * canonicalises before it constructs: the column is 64 characters wide and a zone id is
         * free text of any length (DOM-3). This pins what the validator ACTUALLY admits, so the
         * next reader does not take the shortening for a rule of the domain's own.
         */
        @Test
        @DisplayName("a zone id and a prefix are admitted — the trimming happens at the edge, not here")
        void zone_ids_and_prefixes() {
            assertThatCode(() -> new IpAddress("fe80::1%eth0")).doesNotThrowAnyException();
            assertThatCode(() -> new IpAddress("2001:db8::/32")).doesNotThrowAnyException();

            assertThatThrownBy(() -> new IpAddress("2001:db8::/129"))
                    .as("there are 128 bits to mask, and no more")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IpAddress("fe80::1%eth0%eth1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Tokens")
    class Tokens {

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

            assertThat(AccessToken.random().value())
                    .as("and a minted one always carries something")
                    .isNotBlank();
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
    }
}
