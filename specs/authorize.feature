Feature: Authorizing access with an access token

  A protected resource requires a valid, unexpired access token. The token is obtained by
  authenticating and is presented as a Bearer token.

  Background:
    Given a registered user "user@example.com" with password "StrongPassword1!"

  Rule: 1. A valid access token grants access

    Example:
      Given the user has authenticated
      When the user requests the protected resource with their access token
      Then access is granted

  Rule: 2. A missing or unknown access token is refused

    Scenario Outline: <case>
      When the user requests the protected resource with <case>
      Then access is refused

      Examples:
        | case            |
        | no token        |
        | a garbage token |

  Rule: 3. An expired access token is refused

    Example:
      Given the user has authenticated
      When the access token expires
      And the user requests the protected resource with their access token
      Then access is refused
