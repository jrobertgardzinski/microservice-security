package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The teeth for the address-keyed family of defects, in the spirit of workspace ADR 0006: ONE generic
 * test that states the law once, so the next table keyed by an e-mail address cannot quietly repeat
 * the same four bugs.
 *
 * <p>Identity here is keyed by the e-mail ADDRESS — and the address is mutable, while not one of the
 * tables has a foreign key, so nothing cascades. Every such store therefore owes the account two
 * operations, and P18 found four stores that owed and did not pay:
 *
 * <ol>
 *   <li><b>The move law.</b> After the account's address changes, NOTHING may be left under the old
 *       address. What belongs to the account moves to the new one (factors, recovery codes, the
 *       passwordless mark, federated links, the user row); what was e-mailed to the old address is
 *       dropped (a pending reset, a pending further change). Leaving MFA behind loses the second
 *       factor without a trace; leaving a reset token behind hands the account of the address's next
 *       owner to whoever holds the old link.</li>
 *   <li><b>The purge law.</b> After the account is deleted, NOTHING may be left under its address —
 *       the address is personal data, and a live token matched by address alone would be redeemed
 *       against the next owner of that address.</li>
 * </ol>
 *
 * <p>A new address-keyed table joins the law by joining {@link #storesOf}; forgetting it there is the
 * one remaining failure mode, which is why the list sits next to nothing else.
 *
 * <p>Deliberately NOT in the registry: {@code sessions}. They are covered by the purge law through
 * {@code revokeAllSessions}, but the move path does not re-point them and revoking them on a move is
 * a UX decision (and touches the same use cases as P18 poz. 9), so it is reported rather than
 * silently asserted here.
 */
class AddressKeyedStoresTest {

    private static final Email OLD = Email.of("owner@example.com");
    private static final Email NEW = Email.of("moved@example.com");
    private static final Email THIRD = Email.of("third@example.com");
    private static final List<Email> ADDRESSES = List.of(OLD, NEW);

    private static final VerificationToken CHANGE_TOKEN = new VerificationToken("the-change-token");
    private static final VerificationToken OTHER_CHANGE_TOKEN = new VerificationToken("another-change-token");
    private static final PasswordResetToken RESET_TOKEN = new PasswordResetToken("the-reset-token");

    /**
     * One store keyed by the address: how to put a row under an address, which addresses still hold
     * something afterwards, and whether its content belongs to the ACCOUNT (so it must follow a move)
     * or to the MAILBOX (so a move drops it).
     */
    private record Store(String table, boolean followsTheAccount,
                         Consumer<Email> seed, Supplier<List<Email>> heldUnder) {}

    /** All the adapters and both use cases, freshly wired — one fixture per dynamic test. */
    private static final class Fixture {
        final InMemoryUserRepository users = new InMemoryUserRepository();
        final InMemoryAuthorizationDataRepository sessions =
                new InMemoryAuthorizationDataRepository(Clock.systemUTC());
        final InMemoryEnrolledFactorRepository factors = new InMemoryEnrolledFactorRepository();
        final InMemoryRecoveryCodeRepository codes = new InMemoryRecoveryCodeRepository();
        final InMemoryFederatedIdentityRepository federated = new InMemoryFederatedIdentityRepository();
        final InMemoryPasswordlessAccountRepository passwordless = new InMemoryPasswordlessAccountRepository();
        final InMemoryEmailVerificationRepository verifications = new InMemoryEmailVerificationRepository();
        final InMemoryPasswordResetRepository resets = new InMemoryPasswordResetRepository(Clock.systemUTC());
        final InMemoryEmailChangeRepository changes = new InMemoryEmailChangeRepository(Clock.systemUTC());

        final ConfirmEmailChange confirmEmailChange = new ConfirmEmailChange(changes, users, verifications,
                federated, factors, codes, passwordless, resets,
                java.time.Duration.ofMinutes(1440), Clock.systemUTC());
        final DeleteAccount deleteAccount = new DeleteAccount(users, sessions, factors, codes, federated,
                verifications, resets, changes, passwordless);

        Fixture() {
            users.save(new User(OLD, new HashedPassword("hash")));   // the account every store belongs to
        }
    }

    private static List<Store> storesOf(Fixture f) {
        return List.of(
                new Store("users", true,
                        address -> { /* the account row is part of every fixture */ },
                        () -> heldWhere(address -> f.users.findBy(address).isPresent())),
                new Store("enrolled_factors", true,
                        address -> f.factors.enrol(new EnrolledFactor(address, FactorType.EMAIL_CODE,
                                "e-mail code", 2, address.value())),
                        () -> heldWhere(address -> !f.factors.findByUser(address).isEmpty())),
                new Store("recovery_codes", true,
                        address -> f.codes.replaceAll(address, List.of("code-hash")),
                        () -> heldWhere(address -> f.codes.unusedCount(address) > 0)),
                new Store("passwordless_accounts", true,
                        address -> f.passwordless.setPasswordless(address, true),
                        () -> heldWhere(f.passwordless::isPasswordless)),
                new Store("federated_identities", true,
                        address -> f.federated.link("google", "durable-subject", address),
                        () -> f.federated.findUserBy("google", "durable-subject").stream().toList()),
                // the move VERIFIES the new address (its own token was delivered there), so this row
                // is expected under the new address as well — hence "follows"
                new Store("email_verifications", true,
                        f.verifications::markVerified,
                        () -> heldWhere(f.verifications::isVerified)),
                // no find-by-address on these two ports by design (a token store is queried by token),
                // so the probe consumes the seeded token once and reports whose address came back
                new Store("password_resets", false,
                        address -> f.resets.startReset(address, RESET_TOKEN),
                        () -> f.resets.consumeReset(RESET_TOKEN)
                                .map(PasswordResetRepository.PendingReset::email).stream().toList()),
                new Store("email_changes", false,
                        address -> f.changes.startChange(new EmailChange(address, THIRD), OTHER_CHANGE_TOKEN),
                        () -> f.changes.confirmChange(OTHER_CHANGE_TOKEN)
                                .map(pending -> pending.change().currentEmail()).stream().toList()));
    }

    private static List<Email> heldWhere(Predicate<Email> holdsSomething) {
        return ADDRESSES.stream().filter(holdsSomething).toList();
    }

    @TestFactory
    Stream<DynamicTest> nothing_keyed_by_the_old_address_survives_a_move() {
        return storesOf(new Fixture()).stream().map(Store::table).map(table -> DynamicTest.dynamicTest(
                table, () -> {
                    Fixture f = new Fixture();
                    Store store = storesOf(f).stream()
                            .filter(s -> s.table().equals(table)).findFirst().orElseThrow();
                    store.seed().accept(OLD);
                    f.changes.startChange(new EmailChange(OLD, NEW), CHANGE_TOKEN);

                    f.confirmEmailChange.execute(CHANGE_TOKEN);

                    assertEquals(store.followsTheAccount() ? List.of(NEW) : List.of(),
                            store.heldUnder().get(),
                            store.followsTheAccount()
                                    ? table + " must follow the account to its new address"
                                    : table + " was e-mailed to the old address and must be dropped");
                }));
    }

    @TestFactory
    Stream<DynamicTest> nothing_keyed_by_the_address_survives_the_account() {
        return storesOf(new Fixture()).stream().map(Store::table).map(table -> DynamicTest.dynamicTest(
                table, () -> {
                    Fixture f = new Fixture();
                    Store store = storesOf(f).stream()
                            .filter(s -> s.table().equals(table)).findFirst().orElseThrow();
                    store.seed().accept(OLD);

                    f.deleteAccount.execute(OLD);

                    assertEquals(List.of(), store.heldUnder().get(),
                            table + " outlived the account it belonged to");
                }));
    }

    /** The one case the whole family is about, end to end: an unexpiring reset link after a takeover. */
    @TestFactory
    Stream<DynamicTest> a_reset_pending_when_the_account_closes_cannot_be_redeemed_afterwards() {
        return Stream.of(DynamicTest.dynamicTest("the successor of a freed address keeps their password", () -> {
            Fixture f = new Fixture();
            f.resets.startReset(OLD, RESET_TOKEN);

            f.deleteAccount.execute(OLD);
            f.users.save(new User(OLD, new HashedPassword("the-successor-hash")));   // someone else registers it

            Optional<PasswordResetRepository.PendingReset> redeemed = f.resets.consumeReset(RESET_TOKEN);

            assertEquals(Optional.empty(), redeemed,
                    "the old owner's reset link still resolves to the address — it would set the successor's password");
        }));
    }
}
