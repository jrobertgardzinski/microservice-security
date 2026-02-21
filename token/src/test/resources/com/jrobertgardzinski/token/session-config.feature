Feature: Session Config

  Scenario: Default config
    Given a new session configuration is initialized
    When no specific properties are provided
    Then the session config should adhere to the default values:
      | refreshTokenValidityHours | 48 |
      | accessTokenValidityHours  | 48 |

  Scenario: Custom token lifetimes
    Given a new session configuration is initialized
    When the refresh token validity is set to 720 hours
    And the access token validity is set to 1 hour
    Then the refresh token validity should be 720 hours
    And the access token validity should be 1 hour

  Scenario: Zero validity hours is rejected
    Given a new session configuration is initialized
    When the refresh token validity is set to 0 hours
    Then the session configuration should be rejected
