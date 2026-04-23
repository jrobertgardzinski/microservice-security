package com.jrobertgardzinski.security.config;

import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.token.TokenValidityInHours;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Security")
@Feature("Security Configuration - AccessTokenValidityHours")
class AccessTokenValidityInHoursRulesTest {

    @Property
    @Label("Invariant: accepts valid values")
    void acceptsValidValues(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThat(new AccessTokenValidityInHours(new TokenValidityInHours(value)).tokenValidityInHours().value()).isEqualTo(value);
    }

    @Property
    @Label("Invariant: rejects invalid values")
    void rejectsInvalidValues(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenValidityInHours(new TokenValidityInHours(value)));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        return Arbitraries.of(
                Tuple.of("MIN", 1),
                Tuple.of("above MIN", 24)
        );
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("MIN - 1", 0),
                Tuple.of("negative", -1)
        );
    }
}
