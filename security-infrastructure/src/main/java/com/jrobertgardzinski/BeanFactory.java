package com.jrobertgardzinski;

import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
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
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
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
import jakarta.inject.Named;
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

    /**
     * One {@link SourceThrottle} per expensive anonymous endpoint — separate windows, so a burst
     * against one endpoint cannot starve another. Zero disables an instance.
     */
    @Singleton
    @Named("registration")
    SourceThrottle registrationThrottle(
            @io.micronaut.context.annotation.Value("${security.registration.max-per-window:5}") int maxPerWindow,
            @io.micronaut.context.annotation.Value("${security.registration.window-minutes:15}") int windowMinutes,
            Clock clock) {
        return new SourceThrottle(maxPerWindow, java.time.Duration.ofMinutes(windowMinutes), clock);
    }

    @Singleton
    @Named("password-reset")
    SourceThrottle passwordResetThrottle(
            @io.micronaut.context.annotation.Value("${security.password-reset.max-per-window:5}") int maxPerWindow,
            @io.micronaut.context.annotation.Value("${security.password-reset.window-minutes:15}") int windowMinutes,
            Clock clock) {
        return new SourceThrottle(maxPerWindow, java.time.Duration.ofMinutes(windowMinutes), clock);
    }

    @Singleton
    @Named("verification")
    SourceThrottle verificationThrottle(
            @io.micronaut.context.annotation.Value("${security.verification.max-per-window:5}") int maxPerWindow,
            @io.micronaut.context.annotation.Value("${security.verification.window-minutes:15}") int windowMinutes,
            Clock clock) {
        return new SourceThrottle(maxPerWindow, java.time.Duration.ofMinutes(windowMinutes), clock);
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

    /** Each bound social-login provider becomes the config layer's own type — the rest of the
     *  code never sees the Micronaut binding shim. */
    @io.micronaut.context.annotation.EachBean(OauthProviderConfig.class)
    com.jrobertgardzinski.security.config.oauth.OauthProviderSettings oauthProvider(
            OauthProviderConfig bound) {
        return bound.settings();
    }

    @Singleton
    com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig challengeCodeConfig(
            @io.micronaut.context.annotation.Value("${security.mfa.code.ttl-minutes:5}") int ttlMinutes,
            @io.micronaut.context.annotation.Value("${security.mfa.code.max-attempts:5}") int maxAttempts,
            @io.micronaut.context.annotation.Value("${security.mfa.code.length:6}") int length) {
        return new com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig(ttlMinutes, maxAttempts, length);
    }

    /** One {@link com.jrobertgardzinski.security.system.mfa.CodeFactor} per configured code channel
     *  (e-mail, SMS): the channel decides the factor type, so a new channel bean is a new factor. */
    @Singleton
    java.util.List<com.jrobertgardzinski.security.system.mfa.CodeFactor> codeFactors(
            java.util.List<com.jrobertgardzinski.security.domain.port.CodeChannel> channels,
            com.jrobertgardzinski.security.system.mfa.CodeHasher codeHasher,
            com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig challengeCodeConfig,
            Clock clock) {
        return channels.stream()
                .map(channel -> new com.jrobertgardzinski.security.system.mfa.CodeFactor(
                        channel, codeHasher, challengeCodeConfig, clock))
                .toList();
    }

    /** The authenticator-app (TOTP) factor: self-contained, no channel. */
    @Singleton
    com.jrobertgardzinski.security.system.mfa.TotpFactor totpFactor(
            Clock clock, @io.micronaut.context.annotation.Value("${security.mfa.totp.issuer:security}") String issuer) {
        return new com.jrobertgardzinski.security.system.mfa.TotpFactor(clock, issuer);
    }

    /** The WebAuthn / passkey factor: pure-JDK signature verification, no library. The proof that
     *  the factor port is plug-and-play — one more bean, no change to the chain. */
    @Singleton
    com.jrobertgardzinski.security.system.mfa.WebauthnFactor webauthnFactor(Clock clock,
            @io.micronaut.context.annotation.Value("${security.webauthn.rp-id:localhost}") String rpId,
            @io.micronaut.context.annotation.Value("${security.webauthn.rp-name:Security}") String rpName,
            @io.micronaut.context.annotation.Value("${security.webauthn.origins:`http://localhost:4200,http://localhost:8080`}")
                    String origins,
            @io.micronaut.context.annotation.Value("${security.webauthn.challenge-ttl-minutes:5}") int ttlMinutes) {
        java.util.List<String> allowed = java.util.Arrays.stream(origins.split(","))
                .map(String::trim).filter(o -> !o.isBlank()).toList();
        return new com.jrobertgardzinski.security.system.mfa.WebauthnFactor(clock, rpId, rpName, allowed, ttlMinutes);
    }

    /** Which factor methods this deployment offers = which factor beans are wired. */
    @Singleton
    com.jrobertgardzinski.security.system.mfa.FactorRegistry factorRegistry(
            java.util.List<com.jrobertgardzinski.security.system.mfa.CodeFactor> codeFactors,
            com.jrobertgardzinski.security.system.mfa.TotpFactor totpFactor,
            com.jrobertgardzinski.security.system.mfa.WebauthnFactor webauthnFactor) {
        java.util.List<com.jrobertgardzinski.security.system.mfa.AuthenticationFactor> factors =
                new java.util.ArrayList<>(codeFactors);
        factors.add(totpFactor);
        factors.add(webauthnFactor);
        return new com.jrobertgardzinski.security.system.mfa.FactorRegistry(factors);
    }

    @Singleton
    com.jrobertgardzinski.security.system.mfa.EnrolFactor enrolFactor(
            com.jrobertgardzinski.security.system.mfa.FactorRegistry factorRegistry,
            com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactorRepository,
            com.jrobertgardzinski.security.system.mfa.EnrolmentChallengeStore enrolmentChallengeStore) {
        return new com.jrobertgardzinski.security.system.mfa.EnrolFactor(
                factorRegistry, enrolledFactorRepository, enrolmentChallengeStore);
    }

    /** The factor chain, shared by password sign-in, federated sign-in and the continuation. */
    @Singleton
    com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain(
            com.jrobertgardzinski.security.system.mfa.FactorRegistry factorRegistry,
            com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig challengeCodeConfig,
            com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository recoveryCodeRepository,
            com.jrobertgardzinski.security.system.mfa.CodeHasher codeHasher,
            Clock clock,
            @io.micronaut.context.annotation.Value("${security.mfa.ticket-ttl-minutes:10}") int ticketTtlMinutes) {
        return new com.jrobertgardzinski.security.system.mfa.MfaChain(
                factorRegistry, challengeCodeConfig, recoveryCodeRepository, codeHasher, clock, ticketTtlMinutes);
    }

    @Singleton
    com.jrobertgardzinski.security.config.mfa.RecoveryCodeConfig recoveryCodeConfig(
            @io.micronaut.context.annotation.Value("${security.mfa.recovery.count:10}") int count,
            @io.micronaut.context.annotation.Value("${security.mfa.recovery.length:10}") int length) {
        return new com.jrobertgardzinski.security.config.mfa.RecoveryCodeConfig(count, length);
    }

    @Singleton
    com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes generateRecoveryCodes(
            com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository recoveryCodeRepository,
            com.jrobertgardzinski.security.system.mfa.CodeHasher codeHasher,
            com.jrobertgardzinski.security.config.mfa.RecoveryCodeConfig recoveryCodeConfig) {
        return new com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes(
                recoveryCodeRepository, codeHasher, recoveryCodeConfig);
    }

    /** Start and continue: assembled together so a sign-in begun by one is completed by the other. */
    @Singleton
    AuthenticationFactory.AuthenticationUseCases authenticationUseCases(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            RejectedAuthenticationRepository rejectedAuthenticationRepository,
            AuthenticationBlockRepository authenticationBlockRepository,
            AuthorizationDataRepository authorizationDataRepository,
            HashAlgorithmPort hashAlgorithm,
            BruteForceConfig bruteForceConfig,
            SessionTokensConfig sessionTokensConfig,
            Clock clock,
            BlockDurationPolicy blockDurationPolicy,
            AccessTokenMint accessTokenMint,
            com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactorRepository,
            com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain,
            com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore pendingAuthenticationStore) {
        return AuthenticationFactory.assemble(
                userRepository, emailVerificationRepository, rejectedAuthenticationRepository,
                authenticationBlockRepository, authorizationDataRepository, hashAlgorithm,
                bruteForceConfig, sessionTokensConfig, clock, blockDurationPolicy, accessTokenMint,
                enrolledFactorRepository, mfaChain, pendingAuthenticationStore);
    }

    @Singleton
    Authentication authentication(AuthenticationFactory.AuthenticationUseCases useCases) {
        return useCases.authentication();
    }

    @Singleton
    com.jrobertgardzinski.security.system.authentication.ContinueAuthentication continueAuthentication(
            AuthenticationFactory.AuthenticationUseCases useCases) {
        return useCases.continueAuthentication();
    }

    @Singleton
    RefreshSession refreshSession(
            AuthorizationDataRepository authorizationDataRepository,
            Clock clock,
            SessionTokensConfig sessionTokensConfig,
            AccessTokenMint accessTokenMint) {
        return new RefreshSession(authorizationDataRepository, clock, sessionTokensConfig, accessTokenMint);
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
                                HashAlgorithmPort hashAlgorithm,
                                com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless) {
        return new ResetPassword(passwordResetRepository, userRepository,
                new CreatePasswordHash(hashAlgorithm, PasswordPolicy.withDefaults()), passwordless);
    }

    @Singleton
    com.jrobertgardzinski.security.config.mfa.MfaPolicy mfaPolicy(
            @io.micronaut.context.annotation.Value("${security.mfa.min-factors.user:1}") int user,
            @io.micronaut.context.annotation.Value("${security.mfa.min-factors.moderator:2}") int moderator,
            @io.micronaut.context.annotation.Value("${security.mfa.min-factors.admin:3}") int admin) {
        return new com.jrobertgardzinski.security.config.mfa.MfaPolicy(
                java.util.Map.of("USER", user, "MODERATOR", moderator, "ADMIN", admin));
    }

    @Singleton
    com.jrobertgardzinski.security.config.mfa.StepUpPolicy stepUpPolicy(
            @io.micronaut.context.annotation.Value("${security.step-up.delete-account:FULL_CHAIN}") String deleteAccount,
            @io.micronaut.context.annotation.Value("${security.step-up.change-password:SECOND_FACTORS}") String changePassword) {
        return new com.jrobertgardzinski.security.config.mfa.StepUpPolicy(
                java.util.Map.of("delete-account", deleteAccount, "change-password", changePassword));
    }

    @Singleton
    com.jrobertgardzinski.security.system.mfa.StepUp stepUp(
            com.jrobertgardzinski.security.config.mfa.StepUpPolicy stepUpPolicy,
            UserRepository userRepository,
            HashAlgorithmPort hashAlgorithm,
            com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless,
            com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactors,
            com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain,
            com.jrobertgardzinski.security.system.mfa.StepUpStore stepUpStore,
            com.jrobertgardzinski.security.system.mfa.SessionElevation sessionElevation) {
        return new com.jrobertgardzinski.security.system.mfa.StepUp(
                stepUpPolicy, userRepository, hashAlgorithm, passwordless, enrolledFactors,
                mfaChain, stepUpStore, sessionElevation);
    }

    @Singleton
    com.jrobertgardzinski.security.system.mfa.MfaCompliance mfaCompliance(
            com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactors,
            com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless,
            com.jrobertgardzinski.security.config.mfa.MfaPolicy mfaPolicy,
            @io.micronaut.context.annotation.Value("${security.bootstrap-admins:}") java.util.List<String> bootstrapAdmins) {
        return new com.jrobertgardzinski.security.system.mfa.MfaCompliance(
                enrolledFactors, passwordless, mfaPolicy, java.util.Set.copyOf(bootstrapAdmins));
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
                                          EmailVerificationRepository emailVerificationRepository,
                                          com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository
                                                  federatedIdentityRepository) {
        return new ConfirmEmailChange(emailChangeRepository, userRepository, emailVerificationRepository,
                federatedIdentityRepository);
    }

    @Singleton
    DeleteAccount deleteAccount(UserRepository userRepository, AuthorizationDataRepository authorizationDataRepository) {
        return new DeleteAccount(userRepository, authorizationDataRepository);
    }

    @Singleton
    com.jrobertgardzinski.security.system.federation.FederatedSignIn federatedSignIn(
            com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository federatedIdentities,
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            AuthorizationDataRepository authorizationDataRepository,
            HashAlgorithmPort hashAlgorithm,
            SessionTokensConfig sessionTokensConfig,
            Clock clock,
            AccessTokenMint accessTokenMint,
            com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless,
            com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactors,
            com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain,
            com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore pendingStore) {
        return new com.jrobertgardzinski.security.system.federation.FederatedSignIn(
                federatedIdentities, userRepository, emailVerificationRepository,
                authorizationDataRepository, hashAlgorithm, sessionTokensConfig, clock, accessTokenMint,
                passwordless, enrolledFactors, mfaChain, pendingStore);
    }

    @Singleton
    StartAccountDeletion startAccountDeletion(UserRepository userRepository,
                                              AuthorizationDataRepository authorizationDataRepository,
                                              AccountDeletionSaga saga) {
        return new StartAccountDeletion(userRepository, authorizationDataRepository, saga);
    }
}
