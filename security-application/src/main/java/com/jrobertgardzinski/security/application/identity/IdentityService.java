package com.jrobertgardzinski.security.application.identity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.DisplayName;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.system.identity.DisplayNames;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Who somebody is: the caller to themselves, and the names behind other people's ids. */
public final class IdentityService {

    /** The most ids one question may carry. */
    public static final int MAX_IDS = 100;

    private final RequireRole roles;
    private final MfaCompliance compliance;
    private final UserRepository users;
    private final DisplayNames displayNames;
    private final SourceThrottle displayNamesThrottle;

    public IdentityService(RequireRole roles, MfaCompliance compliance, UserRepository users,
                           DisplayNames displayNames, SourceThrottle displayNamesThrottle) {
        this.roles = roles;
        this.compliance = compliance;
        this.users = users;
        this.displayNames = displayNames;
        this.displayNamesThrottle = displayNamesThrottle;
    }

    public Profile me(Email caller) {
        // rolesInForce, not the persisted grants: a BOOTSTRAP ADMIN holds ADMIN by configuration and
        // has no row saying so. Reading the row alone told such an administrator they were a plain
        // USER — here and in the JWT — while /admin/** let them in, so every consumer that gates on
        // this answer disagreed with the service that issued it.
        Set<Role> inForce = roles.rolesInForce(caller);
        // the MFA role floor, so consumers and the UI can nudge an under-protected privileged account
        return new Profile(
                users.findBy(caller).map(user -> user.id()).orElseThrow(),
                caller,
                inForce,
                compliance.isCompliant(caller, inForce),
                compliance.requiredFactors(inForce),
                compliance.effectiveFactorCount(caller));
    }

    /**
     * The names behind a comma-separated list of ids, in the order asked. Anonymous, like the
     * content that shows them; an id nobody holds is left out, so a deleted account and an
     * invented id look the same.
     */
    public Names displayNames(String ids, IpAddress source) {
        SourceThrottle.Decision decision = displayNamesThrottle.check(source);
        if (!decision.allowed()) {
            return new Names.Throttled(decision.retryAfterSeconds());
        }
        List<UserId> asked;
        try {
            asked = Arrays.stream(ids.split(",")).map(String::trim).filter(id -> !id.isEmpty())
                    .map(UserId::of).distinct().toList();
        } catch (IllegalArgumentException notAnId) {
            return new Names.InvalidId();
        }
        if (asked.size() > MAX_IDS) {
            return new Names.TooManyIds(MAX_IDS);
        }
        Map<UserId, DisplayName> names = displayNames.of(asked);
        return new Names.Found(asked.stream().filter(names::containsKey)
                .map(id -> new Named(id, names.get(id)))
                .toList());
    }

    public record Profile(UserId id, Email email, Set<Role> roles, boolean mfaCompliant,
                          int requiredFactors, int haveFactors) {}

    public record Named(UserId id, DisplayName displayName) {}

    public sealed interface Names {

        record Found(List<Named> names) implements Names {}

        record InvalidId() implements Names {}

        record TooManyIds(int max) implements Names {}

        record Throttled(long retryAfterSeconds) implements Names {}
    }
}
