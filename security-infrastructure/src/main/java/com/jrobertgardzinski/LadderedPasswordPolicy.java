package com.jrobertgardzinski;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Parse;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.config.ladder.Rung;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.RequiresDigit;
import com.jrobertgardzinski.password.config.RequiresLowercase;
import com.jrobertgardzinski.password.config.RequiresUppercase;
import com.jrobertgardzinski.password.config.SpecialChars;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;

/**
 * The password policy in force: every rule on the same ladder - a {@code security_settings} row
 * (live) over the deployment's property (restart) over the library default (rebuild), each
 * declared from the record alone: its {@code ConfigValue} contract says the key and the default,
 * its constructor is the gate. The five ladders are declared once, so a property that is not its type
 * or is below a rule's floor refuses the policy where it is built, at startup, and never on a
 * password; a row like that is refused per resolution and the ladder falls through. The live
 * level is one snapshot of the table, so asking five ladders costs one read.
 */
public final class LadderedPasswordPolicy implements PasswordPolicyInForce {

    private final ConfigLadder<Integer> minLength;
    private final ConfigLadder<String> specialChars;
    private final ConfigLadder<Boolean> requiresUppercase;
    private final ConfigLadder<Boolean> requiresLowercase;
    private final ConfigLadder<Boolean> requiresDigit;

    public LadderedPasswordPolicy(LiveConfigPort<String> rows, RestartConfigPort<String> properties) {
        minLength = ConfigLadder.of(MinLength.DEFAULT.key(), MinLength::new,
                Rung.live(rows, Parse::integer), Rung.restart(properties, Parse::integer),
                Rung.rebuild(MinLength.DEFAULT.defaultValue()));
        specialChars = ConfigLadder.of(SpecialChars.DEFAULT.key(), SpecialChars::new,
                Rung.live(rows, Parse::text), Rung.restart(properties, Parse::text),
                Rung.rebuild(SpecialChars.DEFAULT.defaultValue()));
        requiresUppercase = ConfigLadder.of(RequiresUppercase.DEFAULT.key(), RequiresUppercase::new,
                Rung.live(rows, Parse::bool), Rung.restart(properties, Parse::bool),
                Rung.rebuild(RequiresUppercase.DEFAULT.defaultValue()));
        requiresLowercase = ConfigLadder.of(RequiresLowercase.DEFAULT.key(), RequiresLowercase::new,
                Rung.live(rows, Parse::bool), Rung.restart(properties, Parse::bool),
                Rung.rebuild(RequiresLowercase.DEFAULT.defaultValue()));
        requiresDigit = ConfigLadder.of(RequiresDigit.DEFAULT.key(), RequiresDigit::new,
                Rung.live(rows, Parse::bool), Rung.restart(properties, Parse::bool),
                Rung.rebuild(RequiresDigit.DEFAULT.defaultValue()));
    }

    @Override
    public PasswordPolicy current() {
        return new PasswordPolicy(
                new MinLength(minLength.resolve()),
                new SpecialChars(specialChars.resolve()),
                new RequiresUppercase(requiresUppercase.resolve()),
                new RequiresLowercase(requiresLowercase.resolve()),
                new RequiresDigit(requiresDigit.resolve()));
    }

    /** The minimum length with its provenance: which level answered and what was refused on the way. */
    public Resolution<Integer> minLengthResolution() {
        return minLength.resolution();
    }
}
