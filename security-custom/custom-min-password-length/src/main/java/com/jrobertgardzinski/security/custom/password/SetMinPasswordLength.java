package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.password.config.MinLength;

public class SetMinPasswordLength {

    public static final String KEY = MinPasswordLengthLadder.KEY;

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
