Feature: Refresh token theft detection

  Refresh tokens are single-use: each refresh rotates to a new one. Presenting a refresh token that
  has already been rotated away signals theft, so the whole session lineage is revoked — including
  the attacker's freshly obtained token.

  Background:
    Given a registered user "user@example.com" with password "StrongPassword1!"

  Rule: A replayed (already-rotated) refresh token revokes the whole session family

    Example:
      Given the user has authenticated
      And the user has refreshed the session once
      When the session is refreshed again with the previous refresh token
      Then the refresh is rejected
      And the current refresh token no longer works
