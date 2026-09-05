package com.jrobertgardzinski;

import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import com.jrobertgardzinski.email.config.BlockedDomains;
import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.config.CompanyDomains;
import com.jrobertgardzinski.email.config.DisposableDomains;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm;
import com.jrobertgardzinski.password.application.PasswordPolicyProperties;
import com.jrobertgardzinski.password.application.RestartBoundPasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.roles.BootstrapAdmins;
import com.jrobertgardzinski.security.roles.RequireRole;
import com.jrobertgardzinski.security.roles.RolesOf;
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
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * The deployment's primitives for the password policy, one key per rule
     * ({@code security.password.policy.*}), translated in the library's application layer. Every
     * consumer of the policy — the neutral service and any custom order — reads the deployment
     * through this one object.
     */
    @Singleton
    PasswordPolicyProperties passwordPolicyProperties(RestartConfigPort<String> properties) {
        return new PasswordPolicyProperties(properties);
    }

    /**
     * The password policy of the NEUTRAL service: every rule from its property (restart) over the
     * library default (rebuild). {@code @Secondary} so a custom order that puts a live level above
     * a rule (security-custom) takes precedence by being {@code @Primary}; without such an order
     * this is the policy the use cases ask. {@code @Context} so an illegal property fails the boot.
     */
    @Context
    @Secondary
    PasswordPolicyInForce restartBoundPasswordPolicy(PasswordPolicyProperties properties) {
        return new RestartBoundPasswordPolicy(properties);
    }

    /**
     * Who holds which role, for every admin gate at once: the persisted grants behind the port, and
     * the deployment's bootstrap admins ({@code security.bootstrap-admins}) as the way the first
     * admin exists before any grant.
     */
    @Singleton
    RolesOf rolesOf(UserRepository users) {
        return email -> users.findBy(email).map(User::roles).orElse(Set.of());
    }

    @Singleton
    BootstrapAdmins bootstrapAdmins(@Value("${security.bootstrap-admins:}") List<String> addresses) {
        return BootstrapAdmins.of(addresses);
    }

    @Singleton
    RequireRole requireRole(BootstrapAdmins bootstrapAdmins, RolesOf rolesOf) {
        return new RequireRole(bootstrapAdmins, rolesOf);
    }

    /**
     * The email policy for registration — which domains may not register (blocked, disposable)
     * and, for a closed shop, which ones alone may (company). Deployment-level only for now: the
     * three lists are read from properties once at startup, so a misspelt domain fails the boot
     * rather than the first registration; an absent or empty list is an absent rule. Being fixed
     * for the life of the service, it is handed to the use case as a value — unlike the password
     * policy, which is asked for per attempt. The policy travels with every refusal, next to the
     * password policy, so a client can say WHICH domains an employee may register from — not
     * merely that this one was not among them.
     */
    private static CanRegisterConfig emailPolicy(Environment environment) {
        return new CanRegisterConfig(
                domains(environment, "security.email.blocked.domains", BlockedDomains::new),
                domains(environment, "security.email.disposable.domains", DisposableDomains::new),
                domains(environment, "security.email.company.domains", CompanyDomains::new));
    }

    /** An empty list is a vacant level: {@code null}, which the policy reads as "no such rule". */
    private static <T> T domains(Environment environment, String property, Function<Set<DomainPart>, T> rule) {
        List<String> values = environment.getProperty(property, Argument.listOf(String.class)).orElse(List.of());
        Set<DomainPart> domains = values.stream()
                .map(String::strip).filter(value -> !value.isEmpty())
                .map(DomainPart::of)
                .collect(Collectors.toSet());
        return domains.isEmpty() ? null : rule.apply(domains);
    }

    @Singleton
    Register register(UserRepository userRepository, HashAlgorithmPort hashAlgorithm,
                      PasswordPolicyInForce passwordPolicy, Environment environment) {
        return new Register(userRepository, emailPolicy(environment), hashAlgorithm, passwordPolicy);
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

    // step-up runs behind a live session, but it verifies a password and (for SECOND_FACTORS) mails a
    // code on every start — unthrottled it is a full-speed password oracle and a code mail-bomb. Cap
    // the rate per source, like the anonymous endpoints do.
    @Singleton
    @Named("step-up")
    SourceThrottle stepUpThrottle(
            @io.micronaut.context.annotation.Value("${security.step-up.max-per-window:10}") int maxPerWindow,
            @io.micronaut.context.annotation.Value("${security.step-up.window-minutes:15}") int windowMinutes,
            Clock clock) {
        return new SourceThrottle(maxPerWindow, java.time.Duration.ofMinutes(windowMinutes), clock);
    }

    @Singleton
    com.jrobertgardzinski.security.system.roles.SetUserRoles setUserRoles(UserRepository userRepository) {
        return new com.jrobertgardzinski.security.system.roles.SetUserRoles(userRepository);
    }

    /**
     * Two limits inside one window. The per-account one stays tight (a guessed password is the
     * threat); the per-source ceiling is deliberately far above it, because an address is not a
     * person — behind one there may be an office, a CGNAT or a CI runner, and a number chosen for a
     * single account locks all of them out over somebody else's typos.
     */
    @Singleton
    BruteForceConfig bruteForceConfig(
            @io.micronaut.context.annotation.Value("${security.brute-force.max-failures:3}") int maxFailures,
            @io.micronaut.context.annotation.Value("${security.brute-force.max-failures-per-source:30}") int maxFailuresPerSource) {
        return BruteForceConfig.builder()
                .maxFailures(maxFailures)
                .maxFailuresPerSource(maxFailuresPerSource)
                .build();
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
                                com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless,
                                AuthorizationDataRepository sessions,
                                @io.micronaut.context.annotation.Value("${security.password-reset.ttl-minutes:60}")
                                int resetTtlMinutes,
                                Clock clock,
                                PasswordPolicyInForce passwordPolicy) {
        return new ResetPassword(passwordResetRepository, userRepository,
                hashAlgorithm, passwordPolicy, passwordless,
                sessions, java.time.Duration.ofMinutes(resetTtlMinutes), clock);
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
            @io.micronaut.context.annotation.Value("${security.step-up.change-password:SECOND_FACTORS}") String changePassword,
            // resetting another user's factors is as destructive as deleting an account, so it is
            // FULL_CHAIN by default and pinned here explicitly — an elevation minted for it must not
            // ride over to delete-account (the elevation key carries the action, see SessionElevation)
            @io.micronaut.context.annotation.Value("${security.step-up.admin-reset:FULL_CHAIN}") String adminReset,
            // enrolling or removing a factor rewrites what it takes to sign in; a stolen live session
            // must re-prove itself first, or it could add an attacker-held factor / strip the owner's
            @io.micronaut.context.annotation.Value("${security.step-up.enrol-factor:SECOND_FACTORS}") String enrolFactor,
            @io.micronaut.context.annotation.Value("${security.step-up.remove-factor:SECOND_FACTORS}") String removeFactor,
            // spare keys, the address itself and a granted role: each hands a live session
            // something durable, so each asks for fresh proof (P18 follow-up, StepUpCoverageTest)
            @io.micronaut.context.annotation.Value("${security.step-up.generate-recovery-codes:SECOND_FACTORS}") String recoveryCodes,
            @io.micronaut.context.annotation.Value("${security.step-up.change-email:FULL_CHAIN}") String changeEmail,
            @io.micronaut.context.annotation.Value("${security.step-up.admin-roles:FULL_CHAIN}") String adminRoles,
            // the password policy binds every future password in the estate; a stolen admin
            // session must not be able to lower the floor on a live token alone
            @io.micronaut.context.annotation.Value("${security.step-up.admin-settings:FULL_CHAIN}") String adminSettings) {
        return new com.jrobertgardzinski.security.config.mfa.StepUpPolicy(
                java.util.Map.of("delete-account", deleteAccount, "change-password", changePassword,
                        "admin-reset", adminReset, "enrol-factor", enrolFactor, "remove-factor", removeFactor,
                        "generate-recovery-codes", recoveryCodes, "change-email", changeEmail,
                        "admin-roles", adminRoles, "admin-settings", adminSettings));
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
            com.jrobertgardzinski.security.system.mfa.SessionElevation sessionElevation,
            Clock clock) {
        return new com.jrobertgardzinski.security.system.mfa.StepUp(
                stepUpPolicy, userRepository, hashAlgorithm, passwordless, enrolledFactors,
                mfaChain, stepUpStore, sessionElevation, clock);
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
    ChangePassword changePassword(UserRepository userRepository, HashAlgorithmPort hashAlgorithm,
                                  AuthorizationDataRepository sessions,
                                  PasswordPolicyInForce passwordPolicy) {
        return new ChangePassword(userRepository, hashAlgorithm, passwordPolicy, sessions);
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
                                                  federatedIdentityRepository,
                                          com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository
                                                  enrolledFactorRepository,
                                          com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository
                                                  recoveryCodeRepository,
                                          com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository
                                                  passwordlessAccountRepository,
                                          PasswordResetRepository passwordResetRepository,
                                          @io.micronaut.context.annotation.Value(
                                                  "${security.email-change.ttl-minutes:1440}")
                                          int changeTtlMinutes,
                                          Clock clock) {
        return new ConfirmEmailChange(emailChangeRepository, userRepository, emailVerificationRepository,
                federatedIdentityRepository, enrolledFactorRepository, recoveryCodeRepository,
                passwordlessAccountRepository, passwordResetRepository,
                java.time.Duration.ofMinutes(changeTtlMinutes), clock);
    }

    @Singleton
    DeleteAccount deleteAccount(UserRepository userRepository, AuthorizationDataRepository authorizationDataRepository,
                                com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactorRepository,
                                com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository recoveryCodeRepository,
                                com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository federatedIdentityRepository,
                                EmailVerificationRepository emailVerificationRepository,
                                PasswordResetRepository passwordResetRepository,
                                EmailChangeRepository emailChangeRepository,
                                com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordlessAccountRepository) {
        return new DeleteAccount(userRepository, authorizationDataRepository,
                enrolledFactorRepository, recoveryCodeRepository, federatedIdentityRepository,
                emailVerificationRepository, passwordResetRepository, emailChangeRepository,
                passwordlessAccountRepository);
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
