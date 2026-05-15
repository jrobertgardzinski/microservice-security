package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationEvent;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationPassedEvent;
import com.jrobertgardzinski.security.domain.event.registration.UserAlreadyExistsEvent;
import com.jrobertgardzinski.security.domain.repository.SaveResult;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;

import java.util.function.Function;

public class Register implements Function<UserRegistration, RegistrationEvent> {
    private final UserRepository userRepository;

    public Register(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public RegistrationEvent apply(UserRegistration userRegistration) {
        User user = new User(userRegistration.email(), userRegistration.passwordHash());
        return switch (userRepository.save(user)) {
            case SaveResult.Saved s         -> new RegistrationPassedEvent(s.user().email());
            case SaveResult.AlreadyExists _ -> new UserAlreadyExistsEvent();
        };
    }
}
