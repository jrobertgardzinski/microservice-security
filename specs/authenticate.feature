Feature: Authentication

  Repeated failed AUTHENTICATION attempts from the same source temporarily
  block that source, to protect accounts from password guessing.

  Nouns:
    USER        -> User
    CREDENTIALS -> Credentials
  Verbs:
    AUTHENTICAT* -> Authentication

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"
    And AUTHENTICATION attempts from one source are limited by this policy:
      | failed attempts before the source is blocked | 3                   |
      | failures only count within                   | the last 15 minutes |
      | a block lasts                                | 3 to 10 minutes     |

  Rule: 1. Correct CREDENTIALS AUTHENTICATE the USER

    Example:
      When the USER AUTHENTICATES with the correct CREDENTIALS
      Then the USER is AUTHENTICATED

  Rule: 2. Wrong CREDENTIALS are rejected

    Scenario Outline: <case>
      When the USER tries to AUTHENTICATE with <case>
      Then the AUTHENTICATION is rejected

      Examples:
        | case                       |
        | the wrong password         |
        | an unknown email           |
        | a wrong email and password |

  Rule: 3. Too many failed attempts block the source

    Example: reaching the failure limit blocks the source
      Given the USER has reached the failure limit
      When the USER AUTHENTICATES with the correct CREDENTIALS
      Then the AUTHENTICATION is rejected because the source is blocked

    Example: staying under the limit keeps the source open
      Given the USER has failed to AUTHENTICATE but stayed under the limit
      When the USER AUTHENTICATES with the correct CREDENTIALS
      Then the USER is AUTHENTICATED

  Rule: 4. A blocked source is rejected even with the correct CREDENTIALS

    Example:
      Given the source is blocked
      When the USER AUTHENTICATES with the correct CREDENTIALS
      Then the AUTHENTICATION is rejected because the source is blocked

  Rule: 5. Failed attempts stop counting after 15 minutes

    Example: within 15 minutes the earlier failures still count
      Given the USER has reached the failure limit
      When 14 minutes passes
      And the USER AUTHENTICATES with the correct CREDENTIALS
      Then the AUTHENTICATION is rejected because the source is blocked

    Example: after 15 minutes the earlier failures are forgiven
      Given the USER has reached the failure limit
      When 15 minutes passes
      And the USER AUTHENTICATES with the correct CREDENTIALS
      Then the USER is AUTHENTICATED

  Rule: 6. A block is temporary and expires after a while

    Example:
      Given the source is blocked
      When the block expires
      And the USER AUTHENTICATES with the correct CREDENTIALS
      Then the USER is AUTHENTICATED

  Rule: 7. Correct CREDENTIALS are not enough while the EMAIL is unverified

    Example:
      Given a registered USER "fresh@example.com" with password "StrongPassword1!" whose EMAIL is not verified yet
      When the USER AUTHENTICATES with the correct CREDENTIALS
      Then the AUTHENTICATION is rejected because the EMAIL is not verified
