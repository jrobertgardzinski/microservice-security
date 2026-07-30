package com.jrobertgardzinski.security.system.federation;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.ProviderIdentity;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs a caller in with an identity a provider vouched for — registration collapses into the
 * first sign-in. One account, many identities: the {@code (provider, subject)} link decides, and
 * when there is none yet, the vouched email does:
 * <ul>
 *   <li>no local account &rarr; one is created, verified from birth (the provider's word replaces
 *       our mail loop) and passwordless — its password hash is an unguessable random secret that
 *       verifies nothing, until the owner sets one through the reset flow;</li>
 *   <li>a local account whose email WE verified &rarr; the same inbox proved twice is the same
 *       person: auto-link and sign in;</li>
 *   <li>a local account never verified &rarr; a squatter may have planted it on someone else's
 *       address; the provider's proof beats the unproven password, so the account is taken over —
 *       linked and verified, the old password replaced with an unusable one, every session
 *       revoked.</li>
 * </ul>
 * An assertion whose email the provider does NOT vouch for is refused outright, and an account
 * locked by a running deletion saga refuses federated sign-in just like the password kind.
 */
public class FederatedSignIn {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FederatedIdentityRepository identities;
    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final AuthorizationDataRepository sessions;
    private final HashAlgorithmPort hashAlgorithm;
    private final SessionTokensConfig config;
    private final Clock clock;
    private final AccessTokenMint accessTokenMint;
    private final com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless;
    private final com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactors;
    private final com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain;
    private final com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore pendingStore;

    public FederatedSignIn(FederatedIdentityRepository identities, UserRepository users,
                           EmailVerificationRepository verifications, AuthorizationDataRepository sessions,
                           HashAlgorithmPort hashAlgorithm, SessionTokensConfig config, Clock clock,
                           AccessTokenMint accessTokenMint,
                           com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository passwordless,
                           com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactors,
                           com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain,
                           com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore pendingStore) {
        this.identities = identities;
        this.users = users;
        this.verifications = verifications;
        this.sessions = sessions;
        this.hashAlgorithm = hashAlgorithm;
        this.config = config;
        this.clock = clock;
        this.accessTokenMint = accessTokenMint;
        this.passwordless = passwordless;
        this.enrolledFactors = enrolledFactors;
        this.mfaChain = mfaChain;
        this.pendingStore = pendingStore;
    }

    public FederatedSignInResult execute(ProviderIdentity identity) {
        if (!identity.emailVerified()) {
            return new FederatedSignInResult.Refused("EMAIL_NOT_VOUCHED");
        }
        Optional<Email> linked = identities.findUserBy(identity.provider(), identity.subject())
                // a link pointing at a user that no longer exists (e.g. the email changed
                // locally) is stale — fall through to the email rules and re-link
                .filter(existing -> users.findBy(existing).isPresent());
        // The lock is checked BEFORE claimByEmail, not after. claimByEmail WRITES — it can wipe the
        // password, revoke every session, mark the address verified and link the provider identity —
        // and it used to run first, so an account with a running deletion saga got a fresh provider
        // identity welded onto it moments before it vanished (and, if the saga compensated, came back
        // in a state nobody asked for). Sign-in was refused either way; the side effects were not.
        // The address claimByEmail would act on is exactly the one it returns, so asking about it up
        // front costs nothing: for an unknown address isPendingDeletion is simply false.
        Email account = linked.orElseGet(identity::email);
        if (users.isPendingDeletion(account)) {
            return new FederatedSignInResult.Refused("ACCOUNT_CLOSING");
        }
        if (linked.isEmpty()) {
            claimByEmail(identity);
        }
        // the provider login is link #1; if the account has enrolled factors, they must be passed
        // too before a session — the same chain the password sign-in walks
        java.util.List<com.jrobertgardzinski.security.domain.entity.EnrolledFactor> factors =
                enrolledFactors.findByUser(account);
        if (factors.isEmpty()) {
            return new FederatedSignInResult.SignedIn(sessions.create(
                    SessionTokens.createFor(account, config, clock, accessTokenMint), SessionFamily.start()));
        }
        com.jrobertgardzinski.security.system.mfa.PendingAuthentication pending =
                mfaChain.begin(account, factors);
        String ticket = pendingStore.open(pending);
        return new FederatedSignInResult.MfaRequired(ticket, factors.get(0).type(), pending.challengeData());
    }

    /**
     * Applies the e-mail rules to the vouched address and links the provider identity to it. Writes
     * only; the address it acts on is {@code identity.email()}, which the caller already holds.
     */
    private void claimByEmail(ProviderIdentity identity) {
        Email email = identity.email();
        Optional<User> existing = users.findBy(email);
        if (existing.isEmpty()) {
            users.save(new User(email, unusablePassword()));
            verifications.markVerified(email);
            passwordless.setPasswordless(email, true);   // born through the provider, no password of its own
        } else if (!verifications.isVerified(email)) {
            // the squatter case: the provider's proof of the inbox beats the unproven password
            users.updatePassword(email, unusablePassword());
            sessions.revokeAllSessions(email);
            verifications.markVerified(email);
            passwordless.setPasswordless(email, true);   // the wiped password no longer counts
        }
        // the auto-link case (existing, verified) keeps its password — it stays a password account
        identities.link(identity.provider(), identity.subject(), email);
    }

    /**
     * A hash that verifies no password anyone can type: a discarded 256-bit secret. Real Argon2
     * work on purpose — a malformed sentinel could make the verifier throw instead of refuse.
     */
    private HashedPassword unusablePassword() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return hashAlgorithm.hash(PlaintextPassword.of(Base64.getEncoder().encodeToString(secret)));
    }
}
