package com.jrobertgardzinski;

import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.AuthenticationFactory;
import com.jrobertgardzinski.security.system.authentication.BlockDurationPolicy;
import com.jrobertgardzinski.security.system.authentication.RandomBlockDurationPolicy;
import com.jrobertgardzinski.security.system.authorization.Authorize;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.system.session.ListActiveSessions;
import com.jrobertgardzinski.security.system.session.Logout;
import com.jrobertgardzinski.security.system.session.RefreshSession;
import com.jrobertgardzinski.security.system.session.RevokeAllSessions;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import com.jrobertgardzinski.security.domain.port.AccountDeletionSaga;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import com.jrobertgardzinski.security.system.account.StartAccountDeletion;
import com.jrobertgardzinski.security.system.account.RequestEmailChange;
import com.jrobertgardzinski.security.system.passwordreset.RequestPasswordReset;
import com.jrobertgardzinski.security.system.passwordreset.ResetPassword;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import com.jrobertgardzinski.security.system.verification.VerifyEmail;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * Production wiring for the use cases behind the HTTP entry points. Each use case the controllers
 * call is the very object the application-level Cucumber glue builds — shared behaviour, different
 * entry point. The repositories and the {@link Clock} (system clock in production, a steerable one
 * under the {@code test} environment) are contributed as beans elsewhere and injected here.
 */
@Factory
public class BeanFactory {

    /** One hash algorithm shared by registration (hashing) and authentication (verifying). */
    @Singleton
    HashAlgorithmPort hashAlgorithm() {
        return new Argon2HashAlgorithm();
    }

    @Singleton
    Register register(UserRepository userRepository, HashAlgorithmPort hashAlgorithm) {
        return new Register(
                userRepository,
                CanRegister.builder().build(),
                new CreatePasswordHash(hashAlgorithm, PasswordPolicy.withDefaults()));
    }

    @Singleton
    com.jrobertgardzinski.security.system.registration.RegistrationThrottle registrationThrottle(
            @io.micronaut.context.annotation.Value("${security.registration.max-per-window:5}") int maxPerWindow,
            @io.micronaut.context.annotation.Value("${security.registration.window-minutes:15}") int windowMinutes,
            Clock clock) {
        return new com.jrobertgardzinski.security.system.registration.RegistrationThrottle(
                maxPerWindow, java.time.Duration.ofMinutes(windowMinutes), clock);
    }

    @Singleton
    com.jrobertgardzinski.security.system.roles.SetUserRoles setUserRoles(UserRepository userRepository) {
        return new com.jrobertgardzinski.security.system.roles.SetUserRoles(userRepository);
    }

    @Singleton
    BruteForceConfig bruteForceConfig() {
        return BruteForceConfig.builder().build();
    }

    @Singleton
    SessionTokensConfig sessionTokensConfig() {
        return new SessionTokensConfig(new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1));
    }

    @Singleton
    BlockDurationPolicy blockDurationPolicy(BruteForceConfig bruteForceConfig) {
        return new RandomBlockDurationPolicy(bruteForceConfig);
    }

    @Singleton
    Authentication authentication(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            RejectedAuthenticationRepository rejectedAuthenticationRepository,
            AuthenticationBlockRepository authenticationBlockRepository,
            AuthorizationDataRepository authorizationDataRepository,
            HashAlgorithmPort hashAlgorithm,
            BruteForceConfig bruteForceConfig,
            SessionTokensConfig sessionTokensConfig,
            Clock clock,
            BlockDurationPolicy blockDurationPolicy) {
        return AuthenticationFactory.create(
                userRepository, emailVerificationRepository, rejectedAuthenticationRepository,
                authenticationBlockRepository, authorizationDataRepository, hashAlgorithm,
                bruteForceConfig, sessionTokensConfig, clock, blockDurationPolicy);
    }

    @Singleton
    RefreshSession refreshSession(
            AuthorizationDataRepository authorizationDataRepository,
            Clock clock,
            SessionTokensConfig sessionTokensConfig) {
        return new RefreshSession(authorizationDataRepository, clock, sessionTokensConfig);
    }

    @Singleton
    Authorize authorize(AuthorizationDataRepository authorizationDataRepository, Clock clock) {
        return new Authorize(authorizationDataRepository, clock);
    }

    @Singleton
    Logout logout(AuthorizationDataRepository authorizationDataRepository) {
        return new Logout(authorizationDataRepository);
    }

    @Singleton
    RevokeAllSessions revokeAllSessions(AuthorizationDataRepository authorizationDataRepository) {
        return new RevokeAllSessions(authorizationDataRepository);
    }

    @Singleton
    ListActiveSessions listActiveSessions(AuthorizationDataRepository authorizationDataRepository) {
        return new ListActiveSessions(authorizationDataRepository);
    }

    @Singleton
    RequestEmailVerification requestEmailVerification(
            EmailVerificationRepository emailVerificationRepository, EmailVerificationNotifier notifier) {
        return new RequestEmailVerification(emailVerificationRepository, notifier);
    }

    @Singleton
    VerifyEmail verifyEmail(EmailVerificationRepository emailVerificationRepository) {
        return new VerifyEmail(emailVerificationRepository);
    }

    @Singleton
    RequestPasswordReset requestPasswordReset(
            PasswordResetRepository passwordResetRepository, PasswordResetNotifier notifier) {
        return new RequestPasswordReset(passwordResetRepository, notifier);
    }

    @Singleton
    ResetPassword resetPassword(PasswordResetRepository passwordResetRepository, UserRepository userRepository,
                                HashAlgorithmPort hashAlgorithm) {
        return new ResetPassword(passwordResetRepository, userRepository,
                new CreatePasswordHash(hashAlgorithm, PasswordPolicy.withDefaults()));
    }

    @Singleton
    ChangePassword changePassword(UserRepository userRepository, HashAlgorithmPort hashAlgorithm) {
        return new ChangePassword(userRepository, hashAlgorithm,
                new CreatePasswordHash(hashAlgorithm, PasswordPolicy.withDefaults()));
    }

    @Singleton
    RequestEmailChange requestEmailChange(UserRepository userRepository,
                                          EmailChangeRepository emailChangeRepository,
                                          EmailVerificationNotifier notifier) {
        return new RequestEmailChange(userRepository, emailChangeRepository, notifier);
    }

    @Singleton
    ConfirmEmailChange confirmEmailChange(EmailChangeRepository emailChangeRepository, UserRepository userRepository,
                                          EmailVerificationRepository emailVerificationRepository) {
        return new ConfirmEmailChange(emailChangeRepository, userRepository, emailVerificationRepository);
    }

    @Singleton
    DeleteAccount deleteAccount(UserRepository userRepository, AuthorizationDataRepository authorizationDataRepository) {
        return new DeleteAccount(userRepository, authorizationDataRepository);
    }

    @Singleton
    StartAccountDeletion startAccountDeletion(UserRepository userRepository,
                                              AuthorizationDataRepository authorizationDataRepository,
                                              AccountDeletionSaga saga) {
        return new StartAccountDeletion(userRepository, authorizationDataRepository, saga);
    }
}
