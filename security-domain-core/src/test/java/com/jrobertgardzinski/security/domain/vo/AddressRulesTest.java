package com.jrobertgardzinski.security.domain.vo;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules the domain enforces on the values it is handed, and which nothing was asking about.
 *
 * <p>Every one of these is a guard that only ever fails silently: an address the validator lets
 * through reaches a {@code VARCHAR} column. The tokens' rules live beside the tokens, in the
 * session and mailbox modules. They are cheap to keep honest and expensive to discover broken.
 */
@Epic("Domain")
@Feature("Value rules")
class AddressRulesTest {

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
}
