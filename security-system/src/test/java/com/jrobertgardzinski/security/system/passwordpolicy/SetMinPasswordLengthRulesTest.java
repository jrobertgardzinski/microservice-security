package com.jrobertgardzinski.security.system.passwordpolicy;

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

@Epic("Security")
@Feature("Password policy in force")
@Story("Set minimum password length (never below " + MinLength.BOUNDARY + ")")
class SetMinPasswordLengthRulesTest {

    /** A repository that remembers every value it was asked to keep. */
    private static final class RecordingRepository implements MinLengthRepository {
        final List<MinLength> saved = new ArrayList<>();

        @Override
        public void save(MinLength minLength) {
            saved.add(minLength);
        }
    }

    @Property
    @Label("a length at or above the boundary is ACCEPTED and saved as-is")
    void accepts(@ForAll("legal") int requested) {
        Allure.parameter("requested", requested);
        RecordingRepository store = new RecordingRepository();
        SetMinPasswordLength.Result result = new SetMinPasswordLength(store).execute(requested);
        assertThat(result.status()).isEqualTo(SetMinPasswordLength.Status.ACCEPTED);
        assertThat(result.minLength()).isEqualTo(new MinLength(requested));
        assertThat(result.reason()).isEmpty();
        assertThat(store.saved).containsExactly(new MinLength(requested));
    }

    @Provide
    Arbitrary<Integer> legal() {
        return Arbitraries.integers().between(MinLength.BOUNDARY, 1024);
    }

    @Property
    @Label("a length below the boundary is REFUSED with a reason and NOTHING is saved")
    void refuses(@ForAll("illegal") int requested) {
        Allure.parameter("requested", requested);
        RecordingRepository store = new RecordingRepository();
        SetMinPasswordLength.Result result = new SetMinPasswordLength(store).execute(requested);
        assertThat(result.status()).isEqualTo(SetMinPasswordLength.Status.REFUSED);
        assertThat(result.minLength()).isNull();
        assertThat(result.reason()).isNotBlank();
        assertThat(store.saved).isEmpty();
    }

    @Provide
    Arbitrary<Integer> illegal() {
        return Arbitraries.integers().between(Integer.MIN_VALUE, MinLength.BOUNDARY - 1);
    }

    @Example
    @Label("the use case writes under the library's own key for the rule, the one the ladder reads")
    void sameKeyAsTheLadder() {
        assertThat(SetMinPasswordLength.KEY).isEqualTo(MinLength.KEY);
        assertThat(SetMinPasswordLength.KEY).isEqualTo("security.password.policy.min.length");
    }
}
