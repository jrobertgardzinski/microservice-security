Feature: Refreshing a session

  A USER keeps their session alive by REFRESHING it. A session that has expired,
  or that no longer exists, cannot be REFRESHED — the USER must AUTHENTICATE again.

  Nouns:
    USER -> User
  Verbs:
    REFRESH* -> RefreshSession

  Background:
    Given a registered USER "user@example.com"

  Rule: 1. An active session can be REFRESHED

    Example:
      Given the USER has an active session
      When the USER REFRESHES the session
      Then a fresh session is returned

  Rule: 2. An expired session cannot be REFRESHED

    Example:
      Given the USER'S session has expired
      When the USER REFRESHES the session
      Then the REFRESH is rejected because the session has expired

  Rule: 3. A missing session cannot be REFRESHED

    Example:
      Given the USER has no session
      When the USER REFRESHES the session
      Then the REFRESH is rejected because there is no session to REFRESH
