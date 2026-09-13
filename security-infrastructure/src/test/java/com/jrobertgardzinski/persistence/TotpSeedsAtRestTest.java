package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TotpSecretCipher;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the {@code enrolled_factors} table actually holds — which is the only place this claim can
 * be checked, because "encrypted at rest" is a statement about a disk.
 *
 * <p>{@code EnrolledFactor}'s javadoc, V11 and {@code docs/mfa-design.md} all promised it and the
 * column held the seed in the clear. A TOTP seed is not a credential somebody presents once: it
 * MINTS the codes, for ever, silently — so a copy of this table was a copy of every authenticator
 * app in it, and no phone would ever show a sign of it.
 */
@Testcontainers(disabledWithoutDocker = true)
class TotpSeedsAtRestTest {

    private static final Email USER = Email.of("totp-at-rest@example.com");
    private static final String SEED = "JBSWY3DPEHPK3PXP";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static ApplicationContext context;

    @BeforeAll
    static void startContext() {
        context = ApplicationContext.run(Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.dialect", "POSTGRES",
                "flyway.datasources.default.enabled", true));
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("the seed is ciphertext in the row and the seed again in the domain")
    void a_totp_seed_never_reaches_the_column_in_the_clear() {
        EnrolledFactorRepository factors = context.getBean(EnrolledFactorRepository.class);
        EnrolledFactorJdbcRepository rows = context.getBean(EnrolledFactorJdbcRepository.class);

        factors.enrol(new EnrolledFactor(USER, FactorType.TOTP, "authenticator app", 0, SEED));

        String inTheColumn = rows.findByUserEmailOrderByFactorOrder(USER.value()).get(0).secretMaterial();
        assertThat(inTheColumn)
                .as("a dump of this table would hand over every second factor in it")
                .doesNotContain(SEED)
                .startsWith(TotpSecretCipher.MARKER);

        assertThat(factors.findByUser(USER).get(0).secretMaterial())
                .as("and the factor still verifies codes, because the adapter is the only layer"
                        + " that knows about any of this")
                .isEqualTo(SEED);
    }

    @Test
    @DisplayName("a seed written before the cipher existed keeps working")
    void an_older_row_is_read_as_it_is() {
        EnrolledFactorRepository factors = context.getBean(EnrolledFactorRepository.class);
        EnrolledFactorJdbcRepository rows = context.getBean(EnrolledFactorJdbcRepository.class);
        Email older = Email.of("totp-before-the-key@example.com");

        // exactly what the column held before this change: the seed, in the clear
        String id = EnrolledFactorEntity.keyOf(older.value(), FactorType.TOTP.value());
        rows.save(new EnrolledFactorEntity(id, older.value(), FactorType.TOTP.value(),
                "authenticator app", 0, SEED));

        assertThat(factors.findByUser(older).get(0).secretMaterial())
                .as("no data migration: an unmarked value is returned as it is, so nobody's"
                        + " authenticator stops working on the deploy that introduces the key")
                .isEqualTo(SEED);
    }

    @Test
    @DisplayName("the address a code is sent to is NOT encrypted")
    void the_other_factors_material_is_left_alone() {
        EnrolledFactorRepository factors = context.getBean(EnrolledFactorRepository.class);
        EnrolledFactorJdbcRepository rows = context.getBean(EnrolledFactorJdbcRepository.class);
        Email byMail = Email.of("code-target@example.com");

        factors.enrol(new EnrolledFactor(byMail, FactorType.EMAIL_CODE, "e-mail code", 0, byMail.value()));

        assertThat(rows.findByUserEmailOrderByFactorOrder(byMail.value()).get(0).secretMaterial())
                .as("it is where the next code is SENT, and reassign matches on it when an account"
                        + " moves — encrypting it would cost the same and protect nothing")
                .isEqualTo(byMail.value());
    }
}
