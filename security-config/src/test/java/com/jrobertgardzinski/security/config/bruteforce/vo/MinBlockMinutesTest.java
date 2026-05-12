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
@Story("Min Block Minutes Configuration")
class MinBlockMinutesTest {
    @Property
    @Label("rejects")
    void invariantsRejects(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> new MinBlockMinutes(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("Min int", Integer.MIN_VALUE),
                Tuple.of("MIN - 1", MinBlockMinutes.MIN - 1),
                Tuple.of("MAX + 1", MinBlockMinutes.MAX + 1),
                Tuple.of("Max int", Integer.MAX_VALUE)
        );
    }

    @Property
    @Label("accepts")
    void invariantsAccept(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertDoesNotThrow(() -> new MinBlockMinutes(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        Random random = new Random();
        return Arbitraries.of(
                Tuple.of("MIN", MinBlockMinutes.MIN),
                Tuple.of("between", random.nextInt(MinBlockMinutes.MIN + 1, MinBlockMinutes.MAX)),
                Tuple.of("MAX", MinBlockMinutes.MAX)
        );
    }
}
