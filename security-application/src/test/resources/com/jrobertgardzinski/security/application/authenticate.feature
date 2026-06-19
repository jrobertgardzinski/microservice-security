Feature: Authentication

  Repeated failed authentication attempts from the same source temporarily
  block that source, to protect accounts from password guessing.

  Background:
    Given a registered user "user@example.com" with password "StrongPassword1!"
    And authentication attempts from one source are limited by this policy:
      | failed attempts before the source is blocked | 3                   |
      | failures only count within                   | the last 15 minutes |
      | a block lasts                                | 3 to 10 minutes     |

  Rule: 1. Correct credentials authenticate the user

    Example:
      When the user authenticates with the correct credentials
      Then the user is authenticated

  Rule: 2. Wrong credentials are rejected

    Scenario Outline: <case>
      When the user tries to authenticate with <case>
      Then the authentication is rejected

      Examples:
        | case                       |
        | the wrong password         |
        | an unknown email           |
        | a wrong email and password |

  Rule: 3. Too many failed attempts block the source

    Example: reaching the failure limit blocks the source
      Given the user has reached the failure limit
      When the user authenticates with the correct credentials
      Then the authentication is rejected because the source is blocked

    Example: staying under the limit keeps the source open
      Given the user has failed to authenticate but stayed under the limit
      When the user authenticates with the correct credentials
      Then the user is authenticated

  Rule: 4. A blocked source is rejected even with the correct credentials

    Example:
      Given the source is blocked
      When the user authenticates with the correct credentials
      Then the authentication is rejected because the source is blocked

  Rule: 5. Failed attempts stop counting after 15 minutes

    Example: within 15 minutes the earlier failures still count
      Given the user has reached the failure limit
      When 14 minutes passes
      And the user authenticates with the correct credentials
      Then the authentication is rejected because the source is blocked

    Example: after 15 minutes the earlier failures are forgiven
      Given the user has reached the failure limit
      When 15 minutes passes
      And the user authenticates with the correct credentials
      Then the user is authenticated

  Rule: 6. A block is temporary and expires after a while

    Example:
      Given the source is blocked
      When the block expires
      And the user authenticates with the correct credentials
      Then the user is authenticated
