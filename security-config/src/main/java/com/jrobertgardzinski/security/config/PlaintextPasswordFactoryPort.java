package com.jrobertgardzinski.security.config;

import com.jrobertgardzinski.security.domain.validation.ConfigurablePasswordPolicyAdapter2;
import com.jrobertgardzinski.security.domain.validation.PasswordPolicyConfig;
import com.jrobertgardzinski.security.domain.validation.PasswordPolicyPort2;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

public class PlaintextPasswordFactoryPort {

    private final PasswordPolicyPort2 policy;

    public PlaintextPasswordFactoryPort(PasswordPolicyConfig config) {
        this.policy = new ConfigurablePasswordPolicyAdapter2(config);
    }

    public PlaintextPassword2 create(String rawPassword) {
        return PlaintextPassword2.of(rawPassword, policy);
    }
}
