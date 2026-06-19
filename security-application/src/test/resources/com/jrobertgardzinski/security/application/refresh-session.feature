Feature: Refreshing a session

  A user keeps their session alive by refreshing it. A session that has expired,
  or that no longer exists, cannot be refreshed — the user must authenticate again.

  Background:
    Given a registered user "user@example.com"

  Rule: 1. An active session can be refreshed

    Example:
      Given the user has an active session
      When the user refreshes the session
      Then a fresh session is returned

  Rule: 2. An expired session cannot be refreshed

    Example:
      Given the user's session has expired
      When the user refreshes the session
      Then the refresh is rejected because the session has expired

  Rule: 3. A missing session cannot be refreshed

    Example:
      Given the user has no session
      When the user refreshes the session
      Then the refresh is rejected because there is no session to refresh
