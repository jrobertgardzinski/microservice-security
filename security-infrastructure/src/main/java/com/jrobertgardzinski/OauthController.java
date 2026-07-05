package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.ProviderIdentity;
import com.jrobertgardzinski.security.system.federation.FederatedSignIn;
import com.jrobertgardzinski.security.system.federation.FederatedSignInResult;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HTTP entry points for social sign-in: {@code GET /oauth/{provider}/start} sends the browser to
 * the configured provider (Authorization Code + PKCE, S256), {@code GET /oauth/callback} receives
 * it back, exchanges the code server-side and drives the {@link FederatedSignIn} use case. On
 * success the browser is redirected to the {@code return} URL it asked for — the access token
 * rides in the URL FRAGMENT (never sent to any server; readable by the SPA), the refresh token in
 * the usual HttpOnly cookie. The return URL must match a configured prefix, otherwise the
 * redirect would be an open door for token exfiltration.
 */
// controllers do blocking work (JDBC, the provider's token endpoint) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/oauth")
final class OauthController {

    private final Map<String, OauthProviderConfig> providers;
    private final OauthFlowStore flows;
    private final OidcClient oidc;
    private final FederatedSignIn federatedSignIn;
    private final RefreshCookies refreshCookies;
    private final TransactionBoundary transactionBoundary;
    private final List<String> allowedReturnPrefixes;

    OauthController(List<OauthProviderConfig> providers, OauthFlowStore flows, OidcClient oidc,
                    FederatedSignIn federatedSignIn, RefreshCookies refreshCookies,
                    TransactionBoundary transactionBoundary,
                    @Value("${security.oauth.allowed-return-prefixes:http://localhost:8083/}")
                    List<String> allowedReturnPrefixes) {
        this.providers = providers.stream()
                .collect(java.util.stream.Collectors.toMap(OauthProviderConfig::getName, p -> p));
        this.flows = flows;
        this.oidc = oidc;
        this.federatedSignIn = federatedSignIn;
        this.refreshCookies = refreshCookies;
        this.transactionBoundary = transactionBoundary;
        this.allowedReturnPrefixes = allowedReturnPrefixes;
    }

    @Get(value = "/{provider}/start", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> start(@PathVariable String provider,
                          @Nullable @QueryValue("return") String returnUrl) {
        OauthProviderConfig config = providers.get(provider);
        if (config == null) {
            return HttpResponse.notFound(Map.of("error", "UNKNOWN_PROVIDER"));
        }
        String destination = returnUrl != null ? returnUrl : allowedReturnPrefixes.get(0);
        if (allowedReturnPrefixes.stream().noneMatch(destination::startsWith)) {
            return HttpResponse.badRequest(Map.of("error", "RETURN_URL_NOT_ALLOWED"));
        }
        String codeVerifier = OauthFlowStore.randomToken();
        String nonce = OauthFlowStore.randomToken();
        String state = flows.begin(provider, codeVerifier, nonce, destination);
        String location = config.getAuthorizeUrl() + "?" + query(Map.of(
                "response_type", "code",
                "client_id", config.getClientId(),
                "redirect_uri", config.getRedirectUri(),
                "scope", "openid email",
                "state", state,
                "nonce", nonce,
                "code_challenge", s256(codeVerifier),
                "code_challenge_method", "S256"));
        return HttpResponse.status(HttpStatus.FOUND).header("Location", location);
    }

    @Get(value = "/callback", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> callback(@Nullable @QueryValue String state, @Nullable @QueryValue String code,
                             @Nullable @QueryValue String error) {
        OauthFlowStore.PendingFlow flow = state == null ? null : flows.consume(state).orElse(null);
        if (flow == null) {
            // no flow, no return URL to trust — a bare refusal is all this callback can say
            return HttpResponse.badRequest(Map.of("error", "UNKNOWN_OR_EXPIRED_STATE"));
        }
        if (error != null || code == null) {
            return backTo(flow.returnUrl(), "#oauthError=" + encode(error != null ? error : "missing_code"));
        }
        ProviderIdentity identity;
        try {
            identity = oidc.identityFrom(providers.get(flow.provider()), code, flow.codeVerifier(),
                    flow.nonce());
        } catch (OidcClient.OauthDanceFailed refused) {
            return backTo(flow.returnUrl(), "#oauthError=SIGN_IN_FAILED");
        }
        FederatedSignInResult result = transactionBoundary.execute(() -> federatedSignIn.execute(identity));
        return switch (result) {
            case FederatedSignInResult.SignedIn signedIn -> backTo(flow.returnUrl(),
                    "#accessToken=" + encode(signedIn.session().plainAccessToken()))
                    .cookie(refreshCookies.issue(signedIn.session().plainRefreshToken()));
            case FederatedSignInResult.Refused refused ->
                    backTo(flow.returnUrl(), "#oauthError=" + encode(refused.reason()));
        };
    }

    private static io.micronaut.http.MutableHttpResponse<?> backTo(String returnUrl, String fragment) {
        return HttpResponse.status(HttpStatus.FOUND).header("Location", returnUrl + fragment);
    }

    private static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String query(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
