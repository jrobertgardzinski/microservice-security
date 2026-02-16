package com.jrobertgardzinski.security.config;

import com.jrobertgardzinski.security.domain.validation.password.ConfigurablePasswordPolicyAdapter;
import com.jrobertgardzinski.security.domain.validation.password.PasswordPolicyConfig;
import com.jrobertgardzinski.security.domain.validation.password.PasswordPolicyPort;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;

public class PlaintextPasswordFactoryPort {

    private final PasswordPolicyPort policy;

    public PlaintextPasswordFactoryPort(PasswordPolicyConfig config) {
        this.policy = new ConfigurablePasswordPolicyAdapter(config);
    }

    public PlaintextPassword create(String rawPassword) {
        return PlaintextPassword.of(rawPassword, policy);
    }
}
