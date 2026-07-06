# OAuth providers — configuration recipes

Social sign-in is **pure configuration**: one entry under
`security.oauth.providers.<name>.*` per provider, and `GET /oauth/providers`
tells the UI which buttons to draw. The dance (Authorization Code + PKCE S256,
single-use state, allow-listed return URL, access token in the fragment) never
changes; what differs between providers is how the identity is asserted once
the code is exchanged — the `identity-source`:

| source     | who                     | how the identity arrives                              |
|------------|-------------------------|-------------------------------------------------------|
| `ID_TOKEN` | Google, GitLab, stub IdP | full OIDC: signed `id_token` from the token endpoint (iss/aud/exp/nonce validated hard) |
| `USERINFO` | GitHub, Facebook         | plain OAuth2: the access token is spent server-side on `GET userinfo-url`; fields mapped by config |

Common keys: `authorize-url` (browser-facing), `token-url` (server-side),
`client-id`, `client-secret`, `redirect-uri`, `scope` (default `openid email`),
`label` (button text, defaults to the capitalised name).

USERINFO keys: `userinfo-url`, `subject-field` (default `sub`), `email-field`
(default `email`), `email-verified-field` (default `email_verified`),
`emails-url` (optional; GitHub-shaped array of `{email, primary, verified}`,
consulted when userinfo hides the address or the verified flag),
`assume-email-verified` (default `false` — see the warning below).

## Google (ID_TOKEN) — live today

```yaml
security.oauth.providers.google:
  issuer: https://accounts.google.com
  authorize-url: https://accounts.google.com/o/oauth2/v2/auth
  token-url: https://oauth2.googleapis.com/token
  client-id: <from Google Cloud Console>
  client-secret: <from Google Cloud Console>
  redirect-uri: https://<your-host>/oauth/callback
```

The RS256 signature is accepted on the strength of the direct TLS channel to
the token endpoint (OIDC Core 3.1.3.7); iss/aud/exp/nonce are still checked hard.
In the compose stack the same provider name points at the stub IdP (HS256,
verified against the client secret).

## GitLab (ID_TOKEN)

GitLab is a compliant OIDC provider — same shape as Google:

```yaml
security.oauth.providers.gitlab:
  issuer: https://gitlab.com
  authorize-url: https://gitlab.com/oauth/authorize
  token-url: https://gitlab.com/oauth/token
  scope: openid email
  client-id: <application id>
  client-secret: <application secret>
  redirect-uri: https://<your-host>/oauth/callback
  label: GitLab
```

## GitHub (USERINFO + emails-url)

GitHub speaks no OIDC: no id_token, numeric `id`, and the e-mail is often
private (null in `/user`) — the truth lives behind `/user/emails`, which also
carries the real `verified` flag:

```yaml
security.oauth.providers.github:
  identity-source: USERINFO
  authorize-url: https://github.com/login/oauth/authorize
  token-url: https://github.com/login/oauth/access_token
  userinfo-url: https://api.github.com/user
  emails-url: https://api.github.com/user/emails
  scope: read:user user:email
  subject-field: id
  client-id: <OAuth app client id>
  client-secret: <OAuth app client secret>
  redirect-uri: https://<your-host>/oauth/callback
  label: GitHub
```

The primary verified address wins; an unverified-only account never signs in.

## Facebook (USERINFO + assume-email-verified)

Facebook's Graph API returns an e-mail but **never states whether it is
verified**. Accepting it is therefore a per-deployment decision, made explicit:

```yaml
security.oauth.providers.facebook:
  identity-source: USERINFO
  authorize-url: https://www.facebook.com/v19.0/dialog/oauth
  token-url: https://graph.facebook.com/v19.0/oauth/access_token
  userinfo-url: https://graph.facebook.com/v19.0/me?fields=id,email
  scope: email
  subject-field: id
  assume-email-verified: true   # ⚠ deliberate: Facebook vouches for nothing
  client-id: <app id>
  client-secret: <app secret>
  redirect-uri: https://<your-host>/oauth/callback
  label: Facebook
```

⚠ **`assume-email-verified` links provider identities to local accounts on an
address nobody vouched for.** Without it (the default) a provider that states
no verification bounces back with `EMAIL_NOT_VOUCHED` and no session — the
safe refusal. Turn it on only when you accept that a Facebook account whose
e-mail was never confirmed could reach the matching local account.

## The compose stack

Both flavours run against the same stub IdP (`microservice-idp`): `google`
exercises ID_TOKEN, `github` exercises USERINFO (`/userinfo` on the stub).
`infra-smoke.sh` proves both paths live.
