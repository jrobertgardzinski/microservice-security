package com.jrobertgardzinski;

import com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class BeanFactory {

    @Singleton
    public Argon2HashAlgorithm argon2HashAlgorithm() {
        return new Argon2HashAlgorithm();
    }
}
