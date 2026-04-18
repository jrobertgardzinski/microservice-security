Feature: refresh session

    Background:
    Given a registered user with email "user@example.com"

    Rule: 1. A valid refresh token returns new session tokens

        Example:
        Given the user has an active session
        When the user requests a session refresh
        Then new session tokens are returned

    Rule: 2. An expired refresh token is rejected

        Example:
        Given the user's session has expired
        When the user requests a session refresh
        Then the session refresh is rejected with token expired

    Rule: 3. A non-existent refresh token is rejected

        Example:
        Given the user has no active session
        When the user requests a session refresh
        Then the session refresh is rejected with token not found
