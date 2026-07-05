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
 */
@EachProperty("security.oauth.providers")
public class OauthProviderConfig {

    private final String name;
    private String issuer;
    private String authorizeUrl;
    private String tokenUrl;
    private String userinfoUrl;
    private String clientId;
    private String clientSecret;
    private String redirectUri;

    public OauthProviderConfig(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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
}
