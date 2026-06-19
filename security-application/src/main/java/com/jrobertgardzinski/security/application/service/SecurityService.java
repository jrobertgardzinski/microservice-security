package com.jrobertgardzinski.security.application.service;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.system.authentication.AuthenticationResult;
import com.jrobertgardzinski.security.system.session.RefreshSession;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.registration.RegisterResult;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;

public class SecurityService {
    private final Register register;
    private final RefreshSession refreshSession;
    private final Authentication authentication;

    public SecurityService(Register register, RefreshSession refreshSession, Authentication authentication) {
        this.register = register;
        this.refreshSession = refreshSession;
        this.authentication = authentication;
    }

    public RegisterResult register(String email, String password) {
        return register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password));
    }

    public AuthenticationResult authenticate(AuthenticationRequest authenticationRequest) {
        return authentication.execute(authenticationRequest);
    }

    public com.jrobertgardzinski.security.system.session.RefreshSessionResult refreshSession(SessionRefreshRequest sessionRefreshRequest) {
        return refreshSession.execute(sessionRefreshRequest);
    }
}
