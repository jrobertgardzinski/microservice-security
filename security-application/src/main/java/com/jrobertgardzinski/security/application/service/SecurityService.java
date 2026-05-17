package com.jrobertgardzinski.security.application.service;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.RefreshSession;
import com.jrobertgardzinski.security.system.usecase.AuthenticateUseCase;
import com.jrobertgardzinski.security.system.usecase.RegisterResult;
import com.jrobertgardzinski.security.system.usecase.RegisterUseCase;
import com.jrobertgardzinski.security.system.usecase.VerifyEmailOtpUseCase;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenEvent;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.EmailOtpVerificationRequest;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;

public class SecurityService {
    private final RegisterUseCase registerUseCase;
    private final RefreshSession refreshSession;
    private final AuthenticateUseCase authenticateUseCase;
    private final VerifyEmailOtpUseCase verifyEmailOtpUseCase;

    public SecurityService(RegisterUseCase registerUseCase, RefreshSession refreshSession,
                           AuthenticateUseCase authenticateUseCase,
                           VerifyEmailOtpUseCase verifyEmailOtpUseCase) {
        this.registerUseCase = registerUseCase;
        this.refreshSession = refreshSession;
        this.authenticateUseCase = authenticateUseCase;
        this.verifyEmailOtpUseCase = verifyEmailOtpUseCase;
    }

    public RegisterResult register(String email, String password) {
        return registerUseCase.execute(Email.of(email), PlaintextPassword.of(password));
    }

    public AuthenticationResult authenticate(AuthenticationRequest authenticationRequest) {
        return authenticateUseCase.apply(authenticationRequest);
    }

    public AuthenticationResult verifyEmailOtp(EmailOtpVerificationRequest request) {
        return verifyEmailOtpUseCase.apply(request);
    }

    public RefreshTokenEvent refreshSession(SessionRefreshRequest sessionRefreshRequest) {
        return refreshSession.apply(sessionRefreshRequest);
    }
}
