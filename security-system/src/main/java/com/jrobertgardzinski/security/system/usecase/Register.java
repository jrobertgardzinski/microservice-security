package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.event.RegistrationEvent;
import com.jrobertgardzinski.security.domain.repository.SaveResult;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;

public class Register {
    private final UserRepository userRepository;

    public Register(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public RegistrationEvent execute(UserRegistration userRegistration) {
        User user = new User(userRegistration.email(), userRegistration.passwordHash());
        return switch (userRepository.save(user)) {
            case SaveResult.Saved saved     -> new RegistrationEvent.RegistrationPassedEvent(saved.user().email());
            case SaveResult.AlreadyExists _ -> new RegistrationEvent.UserAlreadyExistsEvent();
        };
    }
}
