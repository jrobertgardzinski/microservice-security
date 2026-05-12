package com.jrobertgardzinski.security.config.bruteforce.vo;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Security")
@Feature("Security Configuration - BruteForceConfig")
@Story("Max Block Minutes Configuration")
class MaxBlockMinutesTest {
    @Property
    @Label("rejects")
    void invariantsRejects(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> new MaxBlockMinutes(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("Min int", Integer.MIN_VALUE),
                Tuple.of("MIN - 1", MaxBlockMinutes.MIN - 1),
                Tuple.of("MAX + 1", MaxBlockMinutes.MAX + 1),
                Tuple.of("Max int", Integer.MAX_VALUE)
        );
    }

    @Story("Max Block Minutes Configuration")
    @Property
    @Label("accepts")
    void invariantsAccept(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertDoesNotThrow(() -> new MaxBlockMinutes(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        Random random = new Random();
        return Arbitraries.of(
                Tuple.of("MIN", MaxBlockMinutes.MIN),
                Tuple.of("between", random.nextInt(MaxBlockMinutes.MIN + 1, MaxBlockMinutes.MAX)),
                Tuple.of("MAX", MaxBlockMinutes.MAX)
        );
    }
}
