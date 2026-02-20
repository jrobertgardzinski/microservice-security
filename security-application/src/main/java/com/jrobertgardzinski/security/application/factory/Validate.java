package com.jrobertgardzinski.security.application.factory;

import java.util.function.Supplier;

public class Validate<T> {
    private T result;
    private boolean isFailed;
    private String exceptionMessage;

    public Validate(Supplier<T> supplier) {
        try {
            result = supplier.get();
        } catch (Exception e) {
            isFailed = true;
            exceptionMessage = e.getMessage();
        }
    }

    public boolean isFailure() {
        return isFailed;
    }

    public T getResult() {
        return result;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }
}
