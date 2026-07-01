Feature: Logging out

  Logging out ends the current session: its refresh token can no longer be refreshed and its access
  token no longer authorizes.

  Background:
    Given a registered user "user@example.com" with password "StrongPassword1!"

  Rule: 1. A logged-out session can no longer be refreshed

    Example:
      Given the user has authenticated
      When the user logs out
      And the user tries to refresh the session
      Then the refresh is refused

  Rule: 2. A logged-out access token no longer grants access

    Example:
      Given the user has authenticated
      When the user logs out
      And the user requests the protected resource with the access token
      Then access is refused

  Rule: 3. Logging out without a session still succeeds

    Example:
      When the user logs out
      Then the logout succeeds
