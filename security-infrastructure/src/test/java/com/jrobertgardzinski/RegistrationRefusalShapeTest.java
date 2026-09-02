package com.jrobertgardzinski;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.config.CompanyDomains;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.SpecialChars;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 422 body of a refused registration speaks ONE shape on both channels: one entry per broken
 * rule, keyed by its code and valued by the parameter in force for that attempt, or {@code true}
 * where the rule has none. The email channel used to answer {@code true} for everything because the
 * result carried no email policy; now it carries one, and the company rule names the domains an
 * employee may register from — the denylists deliberately stay {@code true}.
 */
class RegistrationRefusalShapeTest {

    @Test
    @DisplayName("the company rule is valued by the domains an employee MAY register from, sorted")
    void companyRuleNamesTheAllowedDomains() {
        var policy = new CanRegisterConfig(null, null,
                new CompanyDomains(Set.of(DomainPart.of("zeta.example"), DomainPart.of("acme.example"))));

        var errors = SecurityController.emailErrors(List.of("NOT_A_COMPANY_DOMAIN"), policy);

        assertEquals(List.of(Map.of("NOT_A_COMPANY_DOMAIN", List.of("acme.example", "zeta.example"))), errors);
    }

    @Test
    @DisplayName("denylist rules and the format rule carry no parameter: the caller knows what they typed")
    void denylistsStayBare() {
        var errors = SecurityController.emailErrors(
                List.of("RFC_FORMAT_INVALID", "DOMAIN_BLOCKED", "DISPOSABLE_DOMAIN"), new CanRegisterConfig());

        assertEquals(List.of(
                Map.of("RFC_FORMAT_INVALID", true),
                Map.of("DOMAIN_BLOCKED", true),
                Map.of("DISPOSABLE_DOMAIN", true)), errors);
    }

    @Test
    @DisplayName("the password channel keeps its shape: the minimum length and the special characters in force")
    void passwordChannelUnchanged() {
        var policy = PasswordPolicy.defaultsExcept(new MinLength(12), new SpecialChars("#?!"));

        var errors = SecurityController.passwordErrors(
                List.of("MIN_LENGTH_NOT_MET", "SPECIAL_CHAR_REQUIRED", "DIGIT_REQUIRED"), policy);

        assertEquals(List.of(
                Map.of("MIN_LENGTH_NOT_MET", 12),
                Map.of("SPECIAL_CHAR_REQUIRED", "#?!"),
                Map.of("DIGIT_REQUIRED", true)), errors);
    }
}
