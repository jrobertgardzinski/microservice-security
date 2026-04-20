package com.jrobertgardzinski;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

@Controller("/security")
public class SecurityController {

    private final Argon2HashAlgorithm hashAlgorithm;

    public SecurityController(Argon2HashAlgorithm hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    @Get("/hash")
    public String hash(@QueryValue String password) {
        return hashAlgorithm.hash(password);
    }

    @Get("/health")
    public String health() {
        return "OK";
    }
}
