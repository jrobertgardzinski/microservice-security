package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.mfa.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.core.FakeUserRepository;
import com.jrobertgardzinski.security.domain.mfa.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.mfa.FactorType;
import com.jrobertgardzinski.security.system.authentication.ContinueAuthentication;
import com.jrobertgardzinski.security.system.mfa.EnrolFactor;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.core.SourceThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MfaServiceTest {

    private static final Email CALLER = Email.of("user@example.com");

    private final EnrolFactor enrolFactor = Mockito.mock(EnrolFactor.class);
    private final EnrolledFactorRepository enrolledFactors = Mockito.mock(EnrolledFactorRepository.class);
    private final MfaCompliance compliance = Mockito.mock(MfaCompliance.class);
    private final AtomicBoolean spent = new AtomicBoolean();
    private final BooleanSupplier stepUp = () -> {
        spent.set(true);
        return true;
    };
    private final MfaService service = new MfaService(Mockito.mock(ContinueAuthentication.class), enrolFactor,
            enrolledFactors, Mockito.mock(FactorRegistry.class), new FakeUserRepository(), compliance,
            Mockito.mock(GenerateRecoveryCodes.class), Mockito.mock(RecoveryCodeRepository.class),
            new SourceThrottle(5, Duration.ofMinutes(15), Clock.systemUTC()),
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            });

    @Test
    void a_removal_under_the_floor_is_refused_without_spending_the_step_up() {
        Mockito.when(compliance.removalWouldBreakFloor(Mockito.eq(CALLER), Mockito.any())).thenReturn(true);

        assertInstanceOf(MfaService.Removal.WouldBreakFloor.class, service.removeFactor(CALLER, "TOTP", stepUp));

        assertFalse(spent.get());
        Mockito.verify(enrolledFactors, Mockito.never()).remove(Mockito.any(), Mockito.any());
    }

    @Test
    void a_factor_type_that_does_not_exist_costs_no_step_up() {
        assertInstanceOf(MfaService.Enrolment.UnknownFactor.class,
                service.startEnrolment(CALLER, "", null, stepUp));

        assertFalse(spent.get());
        Mockito.verifyNoInteractions(enrolFactor);
    }

    @Test
    void the_email_factor_sends_its_code_to_the_callers_own_address_whatever_the_body_says() {
        Mockito.when(enrolFactor.start(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new EnrolFactor.Result.Started(null));

        service.startEnrolment(CALLER, "EMAIL_CODE", "thief@example.com", stepUp);

        Mockito.verify(enrolFactor).start(CALLER, FactorType.of("EMAIL_CODE"), CALLER.value());
    }
}
