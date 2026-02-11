package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.validation.PasswordPolicyPort2;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@ToString
@EqualsAndHashCode
@Getter
public class PlaintextPassword2 {
    private final String value;

    private PlaintextPassword2(String value) {
        this.value = value;
    }

    public static PlaintextPassword2 of(String value, PasswordPolicyPort2 passwordPolicyPort2) {
        PlaintextPassword2 plaintextPassword2 = new PlaintextPassword2(value);
        List<String> possibleErrors = passwordPolicyPort2.validate(plaintextPassword2);
        if (!possibleErrors.isEmpty()) {
            throw new IllegalArgumentException(possibleErrors.toString());
        }
        else {
            return plaintextPassword2;
        }
    }
}
