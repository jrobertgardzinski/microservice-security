package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Rung;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.RequiresDigit;
import com.jrobertgardzinski.password.config.RequiresLowercase;
import com.jrobertgardzinski.password.config.RequiresUppercase;
import com.jrobertgardzinski.password.config.SpecialChars;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
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

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Security")
@Feature("Custom: minimum password length")
@Story("Password policy in force: minimum length from the ladder, every other rule the deployment's")
class LadderedPasswordPolicyRulesTest {

    private static final String KEY = "any.key";
    private static final int RESTART_LENGTH = 12;

    /** A ladder over two fake levels: a mutable live row and a fixed restart property. */
    private static ConfigLadder<Integer> ladder(AtomicReference<Integer> liveRow, Integer restartProperty) {
        return ConfigLadder.of(KEY, MinLength::new,
                Rung.live(name -> liveRow.get()),
                Rung.restart(name -> restartProperty),
                Rung.rebuild(MinLength.DEFAULT.value()));
    }

    @Property
    @Label("the live level answers → minimum length is the live value")
    void liveWins(@ForAll("legalLength") int live) {
        Allure.parameter("live", live);
        Allure.parameter("restart", RESTART_LENGTH);
        PasswordPolicy policy = new LadderedPasswordPolicy(ladder(new AtomicReference<>(live), RESTART_LENGTH), PasswordPolicy::withDefaults).current();
        assertThat(policy.minLength()).isEqualTo(new MinLength(live));
    }

    @Property
    @Label("whatever the ladder says, the four other rules are the deployment's, untouched")
    void otherRulesAreTheDeployments(@ForAll("legalLength") int live) {
        Allure.parameter("live", live);
        PasswordPolicy deployment = new PasswordPolicy(new MinLength(RESTART_LENGTH), new SpecialChars("#?!"),
                new RequiresUppercase(false), new RequiresLowercase(false), new RequiresDigit(false));
        PasswordPolicy policy = new LadderedPasswordPolicy(ladder(new AtomicReference<>(live), null), () -> deployment).current();
        assertThat(policy.minLength()).isEqualTo(new MinLength(live));
        assertThat(policy.specialChars()).isEqualTo(deployment.specialChars());
        assertThat(policy.requiresUppercase()).isEqualTo(deployment.requiresUppercase());
        assertThat(policy.requiresLowercase()).isEqualTo(deployment.requiresLowercase());
        assertThat(policy.requiresDigit()).isEqualTo(deployment.requiresDigit());
    }

    @Provide
    Arbitrary<Integer> legalLength() {
        return Arbitraries.integers().between(MinLength.BOUNDARY, 256);
    }

    @Example
    @Label("the live level is vacant → minimum length is the restart value")
    void restartWhenLiveVacant() {
        PasswordPolicy policy = new LadderedPasswordPolicy(ladder(new AtomicReference<>(null), RESTART_LENGTH), PasswordPolicy::withDefaults).current();
        assertThat(policy.minLength()).isEqualTo(new MinLength(RESTART_LENGTH));
    }

    @Example
    @Label("both levels are vacant → minimum length is MinLength.DEFAULT")
    void defaultWhenBothVacant() {
        PasswordPolicy policy = new LadderedPasswordPolicy(ladder(new AtomicReference<>(null), null), PasswordPolicy::withDefaults).current();
        assertThat(policy.minLength()).isEqualTo(MinLength.DEFAULT);
    }

    @Example
    @Label("current() re-reads the live level: a changed row is a changed policy")
    void currentFollowsTheLiveRow() {
        AtomicReference<Integer> liveRow = new AtomicReference<>(8);
        LadderedPasswordPolicy inForce = new LadderedPasswordPolicy(ladder(liveRow, null), PasswordPolicy::withDefaults);
        assertThat(inForce.current().minLength()).isEqualTo(new MinLength(8));
        liveRow.set(16);
        assertThat(inForce.current().minLength()).isEqualTo(new MinLength(16));
        liveRow.set(null);
        assertThat(inForce.current().minLength()).isEqualTo(MinLength.DEFAULT);
    }
}
