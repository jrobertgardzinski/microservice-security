package com.jrobertgardzinski.security.application.service;

import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.RefreshSession;
import com.jrobertgardzinski.security.application.usecase.AuthenticateUseCase;
import com.jrobertgardzinski.security.application.usecase.RegisterResult;
import com.jrobertgardzinski.security.application.usecase.RegisterUseCase;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenEvent;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;

public class SecurityService {
    private final RegisterUseCase registerUseCase;
    private final RefreshSession refreshSession;
    private final AuthenticateUseCase authenticateUseCase;

    public SecurityService(RegisterUseCase registerUseCase, RefreshSession refreshSession, AuthenticateUseCase authenticateUseCase) {
        this.registerUseCase = registerUseCase;
        this.refreshSession = refreshSession;
        this.authenticateUseCase = authenticateUseCase;
    }

    public RegisterResult register(String email, String password) {
        return registerUseCase.execute(email, password);
    }

    public AuthenticationResult authenticate(AuthenticationRequest authenticationRequest) {
        return authenticateUseCase.apply(authenticationRequest);
    }

    public RefreshTokenEvent refreshSession(SessionRefreshRequest sessionRefreshRequest) {
        return refreshSession.apply(sessionRefreshRequest);
    }
}
