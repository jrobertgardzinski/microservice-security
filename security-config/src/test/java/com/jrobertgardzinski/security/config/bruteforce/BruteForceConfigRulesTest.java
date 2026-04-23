package com.jrobertgardzinski.security.config.bruteforce;

import com.jrobertgardzinski.security.config.bruteforce.vo.FailureWindowMinutes;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Security")
@Feature("Security Configuration - BruteForceConfig")
class BruteForceConfigRulesTest {

    @Example
    @Label("Invariant: accepts default values")
    void acceptsDefaultValues() {
        BruteForceConfig config = BruteForceConfig.builder().build();
        Allure.parameter("failureWindowMinutes", config.failureWindowMinutes());
        Allure.parameter("maxFailures", config.maxFailures());
        Allure.parameter("minBlockMinutes", config.minBlockMinutes());
        Allure.parameter("maxBlockMinutes", config.maxBlockMinutes());
        assertThat(config.failureWindowMinutes()).isEqualTo(new FailureWindowMinutes(15));
        assertThat(config.maxFailures()).isEqualTo(3);
        assertThat(config.minBlockMinutes()).isEqualTo(3);
        assertThat(config.maxBlockMinutes()).isEqualTo(10);
    }

    @Property
    @Label("Invariant: rejects invalid failureWindowMinutes")
    void rejectsInvalidFailureWindowMinutes(@ForAll("belowMin") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        assertThrows(IllegalArgumentException.class, () ->
                BruteForceConfig.builder().failureWindowMinutes(boundary.get2()).build());
    }

    @Property
    @Label("Invariant: rejects invalid maxFailures")
    void rejectsInvalidMaxFailures(@ForAll("belowMin") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        assertThrows(IllegalArgumentException.class, () ->
                BruteForceConfig.builder().maxFailures(boundary.get2()).build());
    }

    @Property
    @Label("Invariant: rejects invalid minBlockMinutes")
    void rejectsInvalidMinBlockMinutes(@ForAll("belowMin") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        assertThrows(IllegalArgumentException.class, () ->
                BruteForceConfig.builder().minBlockMinutes(boundary.get2()).build());
    }

    @DisplayName("Invariant: ")
    @ParameterizedTest(name = "rejects maxBlockMinutes={0} smaller than minBlockMinutes={1}")
    @MethodSource("maxBlockVsMinBlock")
    void rejectsMaxBlockMinutesSmallerThanMin(int maxBlockMinutes, int minBlockMinutes) {
        Allure.parameter("minBlockMinutes", minBlockMinutes);
        Allure.parameter("maxBlockMinutes", maxBlockMinutes);
        assertThrows(IllegalArgumentException.class, () ->
                BruteForceConfig.builder().minBlockMinutes(minBlockMinutes).maxBlockMinutes(maxBlockMinutes).build());
    }
    static Stream<Arguments> maxBlockVsMinBlock() {
        return Stream.of(Arguments.of(4, 5));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> belowMin() {
        return Arbitraries.of(
                Tuple.of("MIN - 1", 0),
                Tuple.of("negative", -1)
        );
    }
}
