package com.jrobertgardzinski;

import com.jrobertgardzinski.security.config.oauth.OauthProviderSettings;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * Micronaut binding shim for one identity provider under
 * {@code security.oauth.providers.<name>.*} — the actual configuration type, with its defaults
 * and validation, is {@link OauthProviderSettings} in the config layer; this class only carries
 * the raw properties into {@link #settings()}. The compose stack points a provider named
 * {@code google} at the stub IdP; production points the same name at the real thing — the OAuth
 * dance never changes, only these values.
 */
@EachProperty("security.oauth.providers")
class OauthProviderConfig {

    private final String name;
    private String label;
    private OauthProviderSettings.IdentitySource identitySource;
    private String issuer;
    private String authorizeUrl;
    private String tokenUrl;
    private String userinfoUrl;
    private String emailsUrl;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope;
    private String subjectField;
    private String emailField;
    private String emailVerifiedField;
    private boolean assumeEmailVerified;

    OauthProviderConfig(@Parameter String name) {
        this.name = name;
    }

    OauthProviderSettings settings() {
        return new OauthProviderSettings(name, label, identitySource, issuer, authorizeUrl,
                tokenUrl, userinfoUrl, emailsUrl, clientId, clientSecret, redirectUri, scope,
                subjectField, emailField, emailVerifiedField, assumeEmailVerified);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setIdentitySource(OauthProviderSettings.IdentitySource identitySource) {
        this.identitySource = identitySource;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public void setUserinfoUrl(String userinfoUrl) {
        this.userinfoUrl = userinfoUrl;
    }

    public void setEmailsUrl(String emailsUrl) {
        this.emailsUrl = emailsUrl;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setSubjectField(String subjectField) {
        this.subjectField = subjectField;
    }

    public void setEmailField(String emailField) {
        this.emailField = emailField;
    }

    public void setEmailVerifiedField(String emailVerifiedField) {
        this.emailVerifiedField = emailVerifiedField;
    }

    public void setAssumeEmailVerified(boolean assumeEmailVerified) {
        this.assumeEmailVerified = assumeEmailVerified;
    }
}
