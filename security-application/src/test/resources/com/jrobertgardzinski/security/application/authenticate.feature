Feature: authenticate

    Background:
    Given a registered user with email "user@example.com" and password "StrongPassword1!"
    And the authentication attempt comes from IP "192.168.1.1"

    Rule: 1. Valid credentials with no active block return session tokens

        Example:
        Given the IP has no active block and no recent failures
        When the user authenticates with email "user@example.com" and password "StrongPassword1!"
        Then the authentication result is passed
        And session tokens are returned

    Rule: 2. Wrong credentials cause authentication failure

        Scenario Outline: <case>
        Given the IP has no active block and no recent failures
        When the user authenticates with email "<email>" and password "<password>"
        Then the authentication result is failed

        Examples:
            | case                        | email                   | password                  |
            | wrong password              | user@example.com        | WrongButStrongPassword1!  |
            | unknown email               | other@example.com       | StrongPassword1!          |
            | wrong email and password    | other@example.com       | WrongButStrongPassword1!  |

    Rule: 3. Authentication is blocked when brute force protection is active

        Example: IP is actively blocked
        Given the IP has an active block
        When the user authenticates with email "user@example.com" and password "StrongPassword1!"
        Then the authentication result is blocked

        Example: Too many recent failures
        Given the IP has no active block but has reached the failure limit
        When the user authenticates with email "user@example.com" and password "StrongPassword1!"
        Then the authentication result is blocked

    Rule: 4. Only failures within the recent time window count toward the limit

        Scenario Outline:
        Given the IP has no active block and <failures> failures recorded <minutes> minutes ago
        When the user authenticates with email "user@example.com" and password "StrongPassword1!"
        Then the authentication result is <result>

        Examples:
            | failures | minutes | result  |
            | 3        | 14      | blocked |
            | 3        | 15      | passed  |
