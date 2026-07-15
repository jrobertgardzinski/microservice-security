@http-only
Feature: Authorizing access with an access token

  A protected resource requires a valid, unexpired ACCESS TOKEN. The token is obtained by
  AUTHENTICATING and is presented as a Bearer token.

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: 1. A valid ACCESS TOKEN grants access

    Example:
      Given the USER has AUTHENTICATED
      When the USER requests the protected resource with their ACCESS TOKEN
      Then access is granted

  Rule: 2. A missing or unknown ACCESS TOKEN is refused

    Scenario Outline: <case>
      When the USER requests the protected resource with <case>
      Then access is refused

      Examples:
        | case            |
        | no token        |
        | a garbage token |

  Rule: 3. An expired ACCESS TOKEN is refused

    Example:
      Given the USER has AUTHENTICATED
      When the ACCESS TOKEN expires
      And the USER requests the protected resource with their ACCESS TOKEN
      Then access is refused
