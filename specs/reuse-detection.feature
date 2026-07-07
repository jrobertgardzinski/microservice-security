@http-only
Feature: Refresh token theft detection

  REFRESH tokens are single-use: each REFRESH rotates to a new one. Presenting a REFRESH TOKEN that
  has already been rotated away signals theft, so the whole session lineage is revoked — including
  the attacker's freshly obtained token.

  Nouns:
    USER           -> User
    REFRESH TOKEN  -> RefreshToken
    SESSION FAMILY -> SessionFamily
  Verbs:
    REFRESH* -> RefreshSession

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: A replayed (already-rotated) REFRESH TOKEN revokes the whole SESSION FAMILY

    Example:
      Given the USER has AUTHENTICATED
      And the USER has REFRESHED the session once
      When the session is REFRESHED again with the previous REFRESH TOKEN
      Then the REFRESH is rejected
      And the current REFRESH TOKEN no longer works
