package com.jrobertgardzinski;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;
import java.util.Map;

/**
 * The public half of the access-token signing key, as a standard JWK Set. Other services fetch it
 * once (and on an unknown {@code kid}) to verify access-token signatures offline instead of
 * calling {@code /me} — trading revocation awareness for a saved round-trip; the token's
 * {@code exp} bounds how stale they can be.
 */
@Controller("/.well-known")
final class JwksController {

    private final JwtAccessTokenMint mint;

    JwksController(JwtAccessTokenMint mint) {
        this.mint = mint;
    }

    @Get(value = "/jwks.json", produces = MediaType.APPLICATION_JSON)
    Map<String, Object> jwks() {
        return Map.of("keys", List.of(mint.publicJwk()));
    }
}
