package com.jrobertgardzinski.security.config.bruteforce;

import com.jrobertgardzinski.security.config.bruteforce.vo.FailureWindowMinutes;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxBlockMinutes;
import com.jrobertgardzinski.security.config.bruteforce.vo.MaxFailures;
import com.jrobertgardzinski.security.config.bruteforce.vo.MinBlockMinutes;
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
    @Label("Default")
    void acceptsDefaultValues() {
        BruteForceConfig config = BruteForceConfig.builder().build();
        Allure.parameter("default config", config);
        assertThat(config.failureWindowMinutes()).isEqualTo(FailureWindowMinutes.DEFAULT);
        assertThat(config.maxFailures()).isEqualTo(MaxFailures.DEFAULT);
        assertThat(config.minBlockMinutes()).isEqualTo(MinBlockMinutes.DEFAULT);
        assertThat(config.maxBlockMinutes()).isEqualTo(MaxBlockMinutes.DEFAULT);
    }
}
