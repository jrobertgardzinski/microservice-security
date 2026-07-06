package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.ProviderIdentity;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The server-side half of the OAuth dance: exchanges an authorization code (with its PKCE
 * verifier) at the provider's token endpoint and turns the provider's assertion into a validated
 * {@link ProviderIdentity}. Two shapes of assertion exist (see
 * {@link OauthProviderConfig.IdentitySource}):
 *
 * <ul>
 *   <li><b>ID_TOKEN</b> — full OIDC. Claim validation is mandatory and strict: issuer, audience,
 *       expiry and the nonce we minted. Signature: an HS256 id_token (the stub IdP — a
 *       confidential-client secret-keyed JWS) is verified against the client secret; an
 *       asymmetrically-signed one (e.g. Google's RS256) is accepted on the strength of the direct
 *       TLS channel it just arrived through — OIDC Core 3.1.3.7 permits exactly this for the code
 *       flow, and it spares the demo a JWKS cache for each provider.</li>
 *   <li><b>USERINFO</b> — plain OAuth2 (Facebook, GitHub). There is no id_token to validate, so
 *       the freshly-exchanged access token is spent server-side on {@code userinfo-url} and the
 *       identity is read from that JSON through the configured field mapping. The trust anchor is
 *       the same one OIDC leans on above: the response arrived over the TLS channel we opened to
 *       the provider ourselves. No nonce exists in this flow — replay is already excluded by the
 *       single-use state and the PKCE-bound single-use code.</li>
 * </ul>
 */
@Singleton
final class OidcClient {

    /** Anything the provider or its assertion got wrong — one failure mode for the boundary. */
    static final class OauthDanceFailed extends RuntimeException {
        OauthDanceFailed(String message) {
            super(message);
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json;
    private final Clock clock;

    OidcClient(JsonMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    ProviderIdentity identityFrom(OauthProviderConfig provider, String code, String codeVerifier,
                                  String expectedNonce) {
        Map<String, Object> tokens = postForm(provider.getTokenUrl(), Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", provider.getRedirectUri(),
                "client_id", provider.getClientId(),
                "client_secret", provider.getClientSecret(),
                "code_verifier", codeVerifier));
        return switch (provider.getIdentitySource()) {
            case ID_TOKEN -> fromIdToken(provider, tokens, expectedNonce);
            case USERINFO -> fromUserinfo(provider, tokens);
        };
    }

    // --- ID_TOKEN: the OIDC path ------------------------------------------------

    private ProviderIdentity fromIdToken(OauthProviderConfig provider, Map<String, Object> tokens,
                                         String expectedNonce) {
        String idToken = (String) tokens.get("id_token");
        if (idToken == null) {
            throw new OauthDanceFailed("the token endpoint returned no id_token");
        }
        Map<String, Object> claims = validatedClaims(provider, idToken, expectedNonce);

        String subject = (String) claims.get("sub");
        String email = (String) claims.get("email");
        boolean vouched = Boolean.parseBoolean(String.valueOf(claims.get("email_verified")));
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new OauthDanceFailed("the id_token names no subject or email");
        }
        return new ProviderIdentity(provider.getName(), subject, Email.of(email), vouched);
    }

    private Map<String, Object> validatedClaims(OauthProviderConfig provider, String idToken,
                                                String expectedNonce) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new OauthDanceFailed("the id_token is not a compact JWS");
        }
        Map<String, Object> header = decodeJson(parts[0]);
        if ("HS256".equals(header.get("alg"))) {
            verifyHs256(provider.getClientSecret(), parts);
        }
        // other algs (e.g. Google's RS256): trusted because the token arrived straight from the
        // token endpoint over TLS (OIDC Core 3.1.3.7) — the claims below are still checked hard
        Map<String, Object> claims = decodeJson(parts[1]);
        if (provider.getIssuer() != null && !provider.getIssuer().equals(claims.get("iss"))) {
            throw new OauthDanceFailed("issuer mismatch: " + claims.get("iss"));
        }
        if (!provider.getClientId().equals(claims.get("aud"))) {
            throw new OauthDanceFailed("the id_token was minted for another audience");
        }
        long expiry = ((Number) claims.getOrDefault("exp", 0)).longValue();
        if (expiry <= clock.instant().getEpochSecond()) {
            throw new OauthDanceFailed("the id_token is expired");
        }
        if (!expectedNonce.equals(claims.get("nonce"))) {
            throw new OauthDanceFailed("nonce mismatch — possible replay");
        }
        return claims;
    }

    private static void verifyHs256(String clientSecret, String[] parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) {
                throw new OauthDanceFailed("the id_token signature does not verify");
            }
        } catch (GeneralSecurityException e) {
            throw new OauthDanceFailed("HS256 verification failed: " + e.getMessage());
        }
    }

    // --- USERINFO: the plain-OAuth2 path -----------------------------------------

    private ProviderIdentity fromUserinfo(OauthProviderConfig provider, Map<String, Object> tokens) {
        String accessToken = (String) tokens.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new OauthDanceFailed("the token endpoint returned no access_token");
        }
        Map<String, Object> info = getJson(provider.getUserinfoUrl(), accessToken);

        String subject = stringField(info, provider.getSubjectField());
        if (subject == null) {
            throw new OauthDanceFailed("the userinfo response names no '"
                    + provider.getSubjectField() + "'");
        }
        String email = stringField(info, provider.getEmailField());
        Object verifiedClaim = info.get(provider.getEmailVerifiedField());
        boolean vouched = Boolean.parseBoolean(String.valueOf(verifiedClaim));

        // GitHub keeps the truth behind /user/emails: userinfo may say null (private address) and
        // never carries a verified flag — the emails endpoint states both, so it wins when set
        if ((email == null || verifiedClaim == null) && provider.getEmailsUrl() != null) {
            VerifiedEmail primary = primaryVerifiedEmail(provider, accessToken);
            if (primary != null) {
                email = primary.address();
                vouched = true;
            }
        }
        if (email == null) {
            throw new OauthDanceFailed("the provider disclosed no email address");
        }
        // Facebook states no verification at all — accepting it is a per-deployment decision
        if (verifiedClaim == null && provider.isAssumeEmailVerified()) {
            vouched = true;
        }
        return new ProviderIdentity(provider.getName(), subject, Email.of(email), vouched);
    }

    private record VerifiedEmail(String address) {
    }

    /** GitHub-shaped {@code emails-url}: a JSON array of {@code {email, primary, verified}}. */
    private VerifiedEmail primaryVerifiedEmail(OauthProviderConfig provider, String accessToken) {
        List<Map<String, Object>> entries = getJsonArray(provider.getEmailsUrl(), accessToken);
        return entries.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                .sorted((a, b) -> Boolean.compare(
                        Boolean.TRUE.equals(b.get("primary")), Boolean.TRUE.equals(a.get("primary"))))
                .map(e -> stringField(e, "email"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(VerifiedEmail::new)
                .orElse(null);
    }

    /** Field values arrive as whatever JSON type the provider fancies (GitHub's id is a number). */
    private static String stringField(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    // --- Plumbing -----------------------------------------------------------------

    private Map<String, Object> postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            // GitHub answers form-encoded unless JSON is asked for explicitly
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new OauthDanceFailed("the token endpoint refused the code (HTTP "
                        + response.statusCode() + ")");
            }
            return readJson(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OauthDanceFailed("the token endpoint is unreachable: " + e.getMessage());
        }
    }

    private Map<String, Object> getJson(String url, String bearerToken) {
        return readJson(getBytes(url, bearerToken));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> getJsonArray(String url, String bearerToken) {
        try {
            return (List) json.readValue(getBytes(url, bearerToken),
                    io.micronaut.core.type.Argument.listOf(Map.class));
        } catch (IOException e) {
            throw new OauthDanceFailed("unparseable JSON from the provider");
        }
    }

    private byte[] getBytes(String url, String bearerToken) {
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url))
                            .header("Authorization", "Bearer " + bearerToken)
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new OauthDanceFailed("the provider refused the access token (HTTP "
                        + response.statusCode() + ")");
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OauthDanceFailed("the provider is unreachable: " + e.getMessage());
        }
    }

    private Map<String, Object> decodeJson(String base64Url) {
        return readJson(Base64.getUrlDecoder().decode(base64Url));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(byte[] bytes) {
        try {
            return json.readValue(bytes, Map.class);
        } catch (IOException e) {
            throw new OauthDanceFailed("unparseable JSON from the provider");
        }
    }
}
