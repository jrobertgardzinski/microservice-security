package com.jrobertgardzinski.security.system.settings;

import com.jrobertgardzinski.password.security.config.MinLength;

public class SetMinPasswordLength {

    public static final String KEY = "security.password.policy.min.length";

    public enum Status { ACCEPTED, REFUSED }

    public record Result(Status status, MinLength minLength, String reason) {
        static Result accepted(MinLength minLength) {
            return new Result(Status.ACCEPTED, minLength, "");
        }

        static Result refused(String reason) {
            return new Result(Status.REFUSED, null, reason);
        }
    }

    private final MinPasswordLengthStore store;

    public SetMinPasswordLength(MinPasswordLengthStore store) {
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
