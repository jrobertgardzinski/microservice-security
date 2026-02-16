Feature: Password Policy

  Scenario: Default policy
    Given a new password policy configuration is initialized
    When no specific properties are provided
    Then the policy should adhere to the default security rules:
      | minLength        | 12   |
      | requireLowercase | true |
      | requireUppercase | true |
      | requireDigit     | true |
      | specialChars     | #?!  |