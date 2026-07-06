package com.jrobertgardzinski.security.config.oauth;

/**
 * One social-login identity provider, as the deployment configures it
 * ({@code security.oauth.providers.<name>.*}). Config, not code — adding a provider to a
 * deployment is adding one of these, same discipline as the brute-force policy or the MFA
 * challenge-code lifecycle. The infrastructure layer binds the properties and dances the
 * OAuth protocol; every knob a deployment may turn lives here.
 *
 * <p>{@code authorizeUrl} is browser-facing (the user's redirect target), while
 * {@code tokenUrl}/{@code userinfoUrl}/{@code emailsUrl} are called server-side — in containers
 * they may resolve through different hosts, which is why they are configured separately.
 *
 * <p>Providers assert identity in one of two ways ({@link IdentitySource}):
 * <ul>
 *   <li>{@code ID_TOKEN} (default) — full OIDC: the token endpoint returns a signed
 *       {@code id_token} whose claims are validated (Google, GitLab, the stub IdP);</li>
 *   <li>{@code USERINFO} — plain OAuth2 (Facebook, GitHub): no id_token, so the access token is
 *       spent on a GET to {@code userinfoUrl} and identity is read from that JSON, through the
 *       {@code subjectField}/{@code emailField}/{@code emailVerifiedField} mapping. Providers
 *       that hide the address behind a second endpoint (GitHub's {@code /user/emails}) configure
 *       {@code emailsUrl}; providers that never state verification (Facebook) may declare
 *       {@code assumeEmailVerified} — a deliberate deployment decision, not a default.</li>
 * </ul>
 */
public record OauthProviderSettings(
        String name,
        String label,
        IdentitySource identitySource,
        String issuer,
        String authorizeUrl,
        String tokenUrl,
        String userinfoUrl,
        String emailsUrl,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        String subjectField,
        String emailField,
        String emailVerifiedField,
        boolean assumeEmailVerified) {

    /** Where the validated identity comes from once the code is exchanged. */
    public enum IdentitySource { ID_TOKEN, USERINFO }

    public OauthProviderSettings {
        require(name, "name");
        require(authorizeUrl, "authorize-url");
        require(tokenUrl, "token-url");
        require(clientId, "client-id");
        require(clientSecret, "client-secret");
        require(redirectUri, "redirect-uri");
        if (identitySource == null) {
            identitySource = IdentitySource.ID_TOKEN;
        }
        if (identitySource == IdentitySource.USERINFO && isBlank(userinfoUrl)) {
            throw new IllegalArgumentException(
                    "a USERINFO provider needs a userinfo-url (provider '" + name + "')");
        }
        // what the sign-in button should say; defaults to the capitalised provider name
        if (isBlank(label)) {
            label = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        if (isBlank(scope)) {
            scope = "openid email";
        }
        if (isBlank(subjectField)) {
            subjectField = "sub";
        }
        if (isBlank(emailField)) {
            emailField = "email";
        }
        if (isBlank(emailVerifiedField)) {
            emailVerifiedField = "email_verified";
        }
    }

    private static void require(String value, String key) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("an OAuth provider needs a " + key);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
