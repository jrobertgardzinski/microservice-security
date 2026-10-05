package com.jrobertgardzinski.security.config;

import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Security")
@Feature("Security Configuration - RefreshTokenValidityHours")
@Story("Refresh Token Validity [hours] config")
class RefreshTokenValidityInHoursRulesTest {

    @Property
    @Label("accepts")
    void acceptsValidValues(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThat(new RefreshTokenValidityInHours(value).value()).isEqualTo(value);
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        return Arbitraries.of(
                Tuple.of("MIN", 1),
                Tuple.of("above MIN", 168)
        );
    }

    @Property
    @Label("rejects")
    void rejectsInvalidValues(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenValidityInHours(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("MIN - 1", 0),
                Tuple.of("negative", -1)
        );
    }
}
