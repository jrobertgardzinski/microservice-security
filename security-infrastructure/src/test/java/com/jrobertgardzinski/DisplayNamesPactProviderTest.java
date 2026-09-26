package com.jrobertgardzinski;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Verifies the author-directory library's committed pact — the consumer of {@code GET /users?ids=}
 * on behalf of every content service — against the real controller. Skipped, not failed, when the
 * library repo is not checked out next to this one.
 */
@Provider("microservice-security")
@PactFolder("../../author-directory/pacts")
@EnabledIf(value = "consumerPactsCheckedOut",
        disabledReason = "author-directory is not checked out next to this repo")
class DisplayNamesPactProviderTest {

    static boolean consumerPactsCheckedOut() {
        return Files.isDirectory(Path.of("../../author-directory/pacts"));
    }

    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", server.getPort()));
    }

    @State("accounts exist for alice and bob")
    void aliceAndBobExist() {
        UserRepository users = server.getApplicationContext().getBean(UserRepository.class);
        seed(users, "0f8fad5b-d9cb-469f-a165-70867728950e", "alice@example.com");
        seed(users, "7c9e6679-7425-40de-944b-e07fc1f90ae7", "bob@example.com");
    }

    private static void seed(UserRepository users, String id, String email) {
        Email address = Email.of(email);
        if (users.findBy(address).isEmpty()) {
            users.save(new User(UserId.of(id), address, new HashedPassword("hash"),
                    NormalizedEmail.of(address), Set.of(Role.USER)));
        }
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void theNamesShapeTheLibraryReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
