package com.jrobertgardzinski.security.domain.vo.token;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Security")
@Feature("Security Domain - Token Expiration")
class TokenValidityInHoursTest {

    @Story("Token Validity Hours")
    @Property
    @Label("Invariant - rejects")
    void invariantsRejects(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> new TokenValidityInHours(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("Min int", Integer.MIN_VALUE),
                Tuple.of("MIN - 1", TokenValidityInHours.MIN - 1)
        );
    }

    @Story("Token Validity Hours")
    @Property
    @Label("Invariant - accepts")
    void invariantsAccept(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertDoesNotThrow(() -> new TokenValidityInHours(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        Random random = new Random();
        return Arbitraries.of(
                Tuple.of("MIN", TokenValidityInHours.MIN),
                Tuple.of("above MIN", random.nextInt(TokenValidityInHours.MIN + 1, 8760))
        );
    }
}
