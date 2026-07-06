package com.jrobertgardzinski;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * One configured identity provider under {@code security.oauth.providers.<name>.*}. The compose
 * stack points a provider named {@code google} at the stub IdP; production points the same name
 * at the real thing — the OAuth dance never changes, only these URLs and credentials.
 *
 * <p>{@code authorize-url} is browser-facing (the user's redirect target), while {@code
 * token-url}/{@code userinfo-url} are called server-side — in containers they may resolve
 * through different hosts, which is why they are configured separately.
 *
 * <p>Providers assert identity in one of two ways ({@code identity-source}):
 * <ul>
 *   <li>{@link IdentitySource#ID_TOKEN} (default) — full OIDC: the token endpoint returns a signed
 *       {@code id_token} whose claims are validated (Google, GitLab, the stub IdP);</li>
 *   <li>{@link IdentitySource#USERINFO} — plain OAuth2 (Facebook, GitHub): no id_token, so the
 *       access token is spent on a GET to {@code userinfo-url} and identity is read from that
 *       JSON, through the {@code subject-field}/{@code email-field}/{@code email-verified-field}
 *       mapping. Providers that hide the address behind a second endpoint (GitHub's
 *       {@code /user/emails}) configure {@code emails-url}; providers that never state
 *       verification (Facebook) may declare {@code assume-email-verified} — a deliberate
 *       deployment decision, not a default.</li>
 * </ul>
 */
@EachProperty("security.oauth.providers")
public class OauthProviderConfig {

    /** Where the validated identity comes from once the code is exchanged. */
    public enum IdentitySource { ID_TOKEN, USERINFO }

    private final String name;
    private String label;
    private IdentitySource identitySource = IdentitySource.ID_TOKEN;
    private String issuer;
    private String authorizeUrl;
    private String tokenUrl;
    private String userinfoUrl;
    private String emailsUrl;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "openid email";
    private String subjectField = "sub";
    private String emailField = "email";
    private String emailVerifiedField = "email_verified";
    private boolean assumeEmailVerified;

    public OauthProviderConfig(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** What the sign-in button should say; defaults to the capitalised provider name. */
    public String getLabel() {
        return label != null ? label
                : name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public IdentitySource getIdentitySource() {
        return identitySource;
    }

    public void setIdentitySource(IdentitySource identitySource) {
        this.identitySource = identitySource;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getUserinfoUrl() {
        return userinfoUrl;
    }

    public void setUserinfoUrl(String userinfoUrl) {
        this.userinfoUrl = userinfoUrl;
    }

    public String getEmailsUrl() {
        return emailsUrl;
    }

    public void setEmailsUrl(String emailsUrl) {
        this.emailsUrl = emailsUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSubjectField() {
        return subjectField;
    }

    public void setSubjectField(String subjectField) {
        this.subjectField = subjectField;
    }

    public String getEmailField() {
        return emailField;
    }

    public void setEmailField(String emailField) {
        this.emailField = emailField;
    }

    public String getEmailVerifiedField() {
        return emailVerifiedField;
    }

    public void setEmailVerifiedField(String emailVerifiedField) {
        this.emailVerifiedField = emailVerifiedField;
    }

    public boolean isAssumeEmailVerified() {
        return assumeEmailVerified;
    }

    public void setAssumeEmailVerified(boolean assumeEmailVerified) {
        this.assumeEmailVerified = assumeEmailVerified;
    }
}
