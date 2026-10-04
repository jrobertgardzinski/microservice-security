package com.jrobertgardzinski.security.system.identity;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.DisplayName;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** The names to show for a batch of ids. An id with no account is absent, not an error. */
public final class DisplayNames {

    private final UserRepository users;

    public DisplayNames(UserRepository users) {
        this.users = users;
    }

    public Map<UserId, DisplayName> of(Collection<UserId> ids) {
        return users.findAllBy(ids).stream()
                .collect(Collectors.toMap(User::id, user -> DisplayName.of(user.email())));
    }
}
