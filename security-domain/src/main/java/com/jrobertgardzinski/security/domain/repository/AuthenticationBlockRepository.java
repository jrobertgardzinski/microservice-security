package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.vo.Source;

import java.util.Optional;

public interface AuthenticationBlockRepository {
    AuthenticationBlock create(AuthenticationBlock authenticationBlock);
    void removeAllFor(Source source);
    Optional<AuthenticationBlock> findBy(Source source);
}
