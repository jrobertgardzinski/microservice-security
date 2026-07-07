# @http-only, not @ui: session revocation rides the refresh COOKIE, which a cross-origin dev UI
# (vite:4200 -> service:8180, fetch without credentials) never holds — the wire glue owns these
# rules. The UI sign-out still POSTs /logout so a same-origin deployment revokes for real.
@http-only
Feature: Logging out

  LOGGING OUT ends the current session: its REFRESH TOKEN can no longer be REFRESHED and its access
  token no longer authorizes.

  Nouns:
    USER          -> User
    ACCESS TOKEN  -> AccessToken
    REFRESH TOKEN -> RefreshToken
  Verbs:
    LOG*     -> Logout
    REFRESH* -> RefreshSession

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: 1. A LOGGED-OUT session can no longer be REFRESHED

    Example:
      Given the USER has AUTHENTICATED
      When the USER LOGS OUT
      And the USER tries to REFRESH the session
      Then the REFRESH is refused

  Rule: 2. A LOGGED-OUT ACCESS TOKEN no longer grants access

    Example:
      Given the USER has AUTHENTICATED
      When the USER LOGS OUT
      And the USER requests the protected resource with the ACCESS TOKEN
      Then access is refused

  Rule: 3. LOGGING OUT without a session still succeeds

    Example:
      When the USER LOGS OUT
      Then the LOGOUT succeeds
