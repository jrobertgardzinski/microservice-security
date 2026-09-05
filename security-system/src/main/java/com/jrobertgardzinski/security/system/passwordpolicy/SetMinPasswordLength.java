package com.jrobertgardzinski.security.system.passwordpolicy;

import com.jrobertgardzinski.password.config.MinLength;

/**
 * An ADMIN's decision on the minimum password length, made while the system runs. The value
 * object is the only gate: a length below the policy's own floor is refused with its reason and
 * nothing is written. What is written goes under the library's own key, the one the ladder reads.
 */
public class SetMinPasswordLength {

    public static final String KEY = MinLength.KEY;

    public enum Status { ACCEPTED, REFUSED }

    public record Result(Status status, MinLength minLength, String reason) {
        static Result accepted(MinLength minLength) {
            return new Result(Status.ACCEPTED, minLength, "");
        }

        static Result refused(String reason) {
            return new Result(Status.REFUSED, null, reason);
        }
    }

    private final MinLengthRepository store;

    public SetMinPasswordLength(MinLengthRepository store) {
        this.store = store;
    }

    public Result execute(int requested) {
        MinLength minLength;
        try {
            minLength = new MinLength(requested);
        } catch (IllegalArgumentException refused) {
            return Result.refused(refused.getMessage());
        }
        store.save(minLength);
        return Result.accepted(minLength);
    }
}
