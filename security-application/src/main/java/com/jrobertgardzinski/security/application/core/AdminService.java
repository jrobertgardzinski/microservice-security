package com.jrobertgardzinski.security.application.core;

import com.jrobertgardzinski.config.ConfigValue;
import com.jrobertgardzinski.config.Configuration;
import com.jrobertgardzinski.config.LiveKey;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.security.domain.core.Role;
import com.jrobertgardzinski.security.system.core.SetUserRoles;
import com.jrobertgardzinski.security.system.core.SetSetting;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What an administrator changes for everybody: other users' roles, and the rules declared live.
 * The caller has already been checked for the ADMIN role; every write here takes a step-up on top,
 * handed in as a check and asked after everything that could refuse the request without spending it.
 */
public final class AdminService {

    private final SetUserRoles setUserRoles;
    private final SetSetting setSetting;
    private final Configuration configuration;
    private final Supplier<Map<String, Resolution<? extends ConfigValue<?>>>> passwordPolicyInForce;

    public AdminService(SetUserRoles setUserRoles, SetSetting setSetting, Configuration configuration,
                        Supplier<Map<String, Resolution<? extends ConfigValue<?>>>> passwordPolicyInForce) {
        this.setUserRoles = setUserRoles;
        this.setSetting = setSetting;
        this.configuration = configuration;
        this.passwordPolicyInForce = passwordPolicyInForce;
    }

    /**
     * Sets {@code user}'s roles to exactly {@code roles} — a list of role names, as the caller sent
     * it. Both are read BEFORE the step-up, which CONSUMES a one-shot elevation: an address with a
     * typo in it used to be parsed after, so the request died on the address with the elevation
     * already spent — and the retry needed the whole chain walked again.
     */
    public RolesChange setRoles(String user, Object roles, BooleanSupplier stepUp) {
        Email target;
        try {
            target = Email.of(user);
        } catch (IllegalArgumentException notAnAddress) {
            return new RolesChange.InvalidEmail();
        }
        Set<Role> granted;
        try {
            granted = parseRoles(roles);
        } catch (IllegalArgumentException unknownRole) {
            // the roles that exist, not the exception's sentence — which named the enum's class
            return new RolesChange.UnknownRole(Arrays.stream(Role.values()).map(Role::name).sorted().toList());
        }
        // a granted role is a permanent widening of what a session may do, so a stolen admin
        // session must prove itself again before handing that out
        if (!stepUp.getAsBoolean()) {
            return new RolesChange.StepUpRequired();
        }
        SetUserRoles.Result result = setUserRoles.execute(target, granted);
        return switch (result.status()) {
            case UPDATED -> new RolesChange.Updated(result.roles());
            case NO_SUCH_USER -> new RolesChange.NoSuchUser();
            case WOULD_LEAVE_NO_ADMIN -> new RolesChange.WouldLeaveNoAdmin(result.roles());
        };
    }

    /** Every rule declared live, by its key, with what is in force and where it came from. */
    public Map<String, Resolution<? extends ConfigValue<?>>> settings() {
        Map<String, Resolution<? extends ConfigValue<?>>> report = new LinkedHashMap<>();
        configuration.liveKeys().forEach((key, live) -> report.put(key, live.resolution()));
        return report;
    }

    /**
     * Tells one live rule a value, as text — the way a property or a row holds it. The catalogue
     * supplies the rule's own parser and gate, so there is no code per rule.
     */
    public SettingChange setSetting(String key, Object value, BooleanSupplier stepUp) {
        // a rule binds every future decision under it
        if (!stepUp.getAsBoolean()) {
            return new SettingChange.StepUpRequired();
        }
        if (value == null) {
            return new SettingChange.NoValue();
        }
        return settingChange(setSetting.execute(key, String.valueOf(value)));
    }

    /** Every rule of the password policy in force, under its key, in the policy's own order. */
    public Map<String, Resolution<? extends ConfigValue<?>>> passwordPolicy() {
        return passwordPolicyInForce.get();
    }

    /** The minimum password length: the one rule a film follows by name. */
    public SettingChange setMinPasswordLength(Object value, BooleanSupplier stepUp) {
        // it binds every future password in the estate
        if (!stepUp.getAsBoolean()) {
            return new SettingChange.StepUpRequired();
        }
        int requested;
        try {
            requested = Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException notANumber) {
            return new SettingChange.NotANumber();
        }
        return settingChange(setSetting.execute(MinLength.KEY, Integer.toString(requested)));
    }

    private static SettingChange settingChange(SetSetting.Result result) {
        return switch (result.status()) {
            case ACCEPTED -> new SettingChange.Accepted(result.value());
            case REFUSED -> new SettingChange.Refused(result.reason());
            case UNKNOWN_KEY -> new SettingChange.UnknownKey(result.reason());
        };
    }

    @SuppressWarnings("unchecked")
    private static Set<Role> parseRoles(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("roles must be a list");
        }
        return ((List<Object>) list).stream()
                .map(o -> Role.valueOf(String.valueOf(o).trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    public sealed interface RolesChange {

        record Updated(Set<Role> roles) implements RolesChange {}

        record NoSuchUser() implements RolesChange {}

        /**
         * The caller IS allowed to do this; the state of the system refuses — granting somebody
         * else first is the way out, not proving oneself again.
         */
        record WouldLeaveNoAdmin(Set<Role> roles) implements RolesChange {}

        record InvalidEmail() implements RolesChange {}

        record UnknownRole(List<String> known) implements RolesChange {}

        record StepUpRequired() implements RolesChange {}
    }

    public sealed interface SettingChange {

        record Accepted(Object value) implements SettingChange {}

        /** The rule's gate refused the value; nothing was written. */
        record Refused(String reason) implements SettingChange {}

        /** No rule is declared under that key; nothing was written. */
        record UnknownKey(String reason) implements SettingChange {}

        record NoValue() implements SettingChange {}

        record NotANumber() implements SettingChange {}

        record StepUpRequired() implements SettingChange {}
    }
}
