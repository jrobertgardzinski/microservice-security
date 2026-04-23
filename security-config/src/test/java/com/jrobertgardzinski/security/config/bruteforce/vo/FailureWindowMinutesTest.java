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
class FailureWindowMinutesTest {
    @Story("Failure Window Minutes Configuration")
    @Property
    @Label("Invariant - rejects")
    void invariantsRejects(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> new FailureWindowMinutes(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("Min int", Integer.MIN_VALUE),
                Tuple.of("MIN - 1", FailureWindowMinutes.MIN - 1),
                Tuple.of("MAX + 1", FailureWindowMinutes.MAX + 1),
                Tuple.of("Max int", Integer.MAX_VALUE)
        );
    }

    @Story("Failure Window Minutes Configuration")
    @Property
    @Label("Invariant - accepts")
    void invariantsAccept(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertDoesNotThrow(() -> new FailureWindowMinutes(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        Random random = new Random();
        return Arbitraries.of(
                Tuple.of("MIN", FailureWindowMinutes.MIN),
                Tuple.of("between", random.nextInt(FailureWindowMinutes.MIN + 1, FailureWindowMinutes.MAX)),
                Tuple.of("MAX", FailureWindowMinutes.MAX)
        );
    }
}