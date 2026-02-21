Feature: Password Policy Config

  Scenario: Default policy
    Given a new password policy configuration is initialized
    When no specific properties are provided
    Then the policy should adhere to the default security rules:
      | minLength        | 12   |
      | requireLowercase | true |
      | requireUppercase | true |
      | requireDigit     | true |
      | specialChars     | #?!  |

  Scenario: Custom min length
    Given a new password policy configuration is initialized
    When the min length is set to 8
    Then the policy min length should be 8

  Scenario: No special chars required
    Given a new password policy configuration is initialized
    When special chars are disabled
    Then the policy special chars should be empty
