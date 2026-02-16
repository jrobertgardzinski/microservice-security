package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.validation.password.PasswordPolicyPort;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@ToString
@EqualsAndHashCode
@Getter
public class PlaintextPassword {
    private final String value;

    private PlaintextPassword(String value) {
        this.value = value;
    }

    public static PlaintextPassword of(String value, PasswordPolicyPort passwordPolicyPort) {
        PlaintextPassword plaintextPassword = new PlaintextPassword(value);
        List<String> possibleErrors = passwordPolicyPort.validate(plaintextPassword);
        if (!possibleErrors.isEmpty()) {
            throw new IllegalArgumentException(possibleErrors.toString());
        }
        else {
            return plaintextPassword;
        }
    }
}
