package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.system.registration.RegisterResult;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point for registration. This adapter drives the exact same {@link Register}
 * use case that the application-level Cucumber glue drives directly — "same behaviour,
 * different entry point". The HTTP contract:
 * <ul>
 *   <li>{@code Registered}        &rarr; 201 Created, {@code {"id": "<uuid>"}}; a verification
 *       link is e-mailed — sign-in stays blocked until the address is verified</li>
 *   <li>{@code Rejected}          &rarr; 422 Unprocessable Entity,
 *       {@code {"emailErrors": [...], "passwordErrors": [...]}}</li>
 *   <li>{@code EmailAlreadyTaken} &rarr; 409 Conflict, {@code {"error": "EMAIL_ALREADY_TAKEN"}}</li>
 * </ul>
 * 422 (not 400): the JSON is well-formed; the failure is semantic validation of the values.
 */
@Controller("/register")
public class SecurityController {

    private final Register register;
    private final RequestEmailVerification requestEmailVerification;
    private final TransactionBoundary transactionBoundary;

    public SecurityController(Register register, RequestEmailVerification requestEmailVerification,
                              TransactionBoundary transactionBoundary) {
        this.register = register;
        this.requestEmailVerification = requestEmailVerification;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> register(@Body Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        RegisterResult result = transactionBoundary.execute(
                () -> register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password)));

        return switch (result) {
            case RegisterResult.Registered registered -> {
                // sign-in requires a verified address, so onboarding starts the verification here
                transactionBoundary.execute(() -> {
                    requestEmailVerification.execute(Email.of(email));
                    return null;
                });
                yield HttpResponse.<Map<String, Object>>created(Map.of("id", registered.user().id().toString()));
            }
            case RegisterResult.Rejected rejected ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(Map.of(
                                    "emailErrors", rejected.emailErrors().codes(),
                                    "passwordErrors", rejected.passwordErrors().codes()));
            case RegisterResult.EmailAlreadyTaken alreadyTaken ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "EMAIL_ALREADY_TAKEN"));
        };
    }
}
