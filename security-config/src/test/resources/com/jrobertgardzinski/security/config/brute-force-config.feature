Feature: Brute Force Config

  Scenario: Default config
    Given a new brute force configuration is initialized
    When no specific properties are provided
    Then the brute force config should adhere to the default security rules:
      | failureWindowMinutes | 15 |
      | maxFailures          | 3  |
      | minBlockMinutes      | 3  |
      | maxBlockMinutes      | 10 |
