package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.Level;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import com.jrobertgardzinski.password.config.MinLength;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("Security")
@Feature("Custom: minimum password length")
@Story("This order's ladder: one key, MinLength as the gate, live over restart over the library default")
class MinPasswordLengthLadderRulesTest {

    private static final LiveConfigPort<Integer> NO_LIVE_ROW = name -> null;
    private static final RestartConfigPort<Integer> NO_PROPERTY = name -> null;

    @Example
    @Label("the key follows the dotted convention: security.password.policy.min.length")
    void key() {
        assertThat(MinPasswordLengthLadder.KEY).isEqualTo("security.password.policy.min.length");
        assertThat(MinPasswordLengthLadder.over(NO_LIVE_ROW, NO_PROPERTY).key()).isEqualTo(MinPasswordLengthLadder.KEY);
    }

    @Example
    @Label("both ports are asked under exactly that key")
    void portsAreAskedUnderTheKey() {
        List<String> asked = new ArrayList<>();
        LiveConfigPort<Integer> live = name -> { asked.add("live:" + name); return null; };
        RestartConfigPort<Integer> restart = name -> { asked.add("restart:" + name); return null; };
        MinPasswordLengthLadder.over(live, restart).resolve();
        assertThat(asked).containsExactlyInAnyOrder("restart:" + MinPasswordLengthLadder.KEY, "live:" + MinPasswordLengthLadder.KEY);
    }

    @Example
    @Label("no row, no property → MinLength.DEFAULT from the rebuild level")
    void defaultFromRebuild() {
        Resolution<Integer> resolution = MinPasswordLengthLadder.over(NO_LIVE_ROW, NO_PROPERTY).resolution();
        assertThat(resolution.value()).isEqualTo(MinLength.DEFAULT.value());
        assertThat(resolution.source()).isEqualTo(Level.REBUILD.label());
    }

    @Property
    @Label("a live row below the boundary is REJECTED and the ladder falls through to the default")
    void illegalLiveRowFallsThrough(@ForAll("belowBoundary") int row) {
        Allure.parameter("live row", row);
        Resolution<Integer> resolution = MinPasswordLengthLadder.over(name -> row, NO_PROPERTY).resolution();
        assertThat(resolution.value()).isEqualTo(MinLength.DEFAULT.value());
        assertThat(resolution.source()).isEqualTo(Level.REBUILD.label());
        assertThat(resolution.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.source()).isEqualTo(Level.LIVE.label());
            assertThat(rejected.value()).isEqualTo(row);
        });
    }

    @Property
    @Label("a property below the boundary refuses to build the ladder at all, naming the key")
    void illegalPropertyRefusesTheLadder(@ForAll("belowBoundary") int property) {
        Allure.parameter("property", property);
        assertThatThrownBy(() -> MinPasswordLengthLadder.over(NO_LIVE_ROW, name -> property))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MinPasswordLengthLadder.KEY)
                .hasMessageContaining(Level.RESTART.label());
    }

    @Provide
    Arbitrary<Integer> belowBoundary() {
        return Arbitraries.integers().between(-10, MinLength.BOUNDARY - 1);
    }
}
