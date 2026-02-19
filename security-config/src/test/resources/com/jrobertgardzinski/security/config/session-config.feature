Feature: Session Config

  Scenario: Default config
    Given a new session configuration is initialized
    When no specific properties are provided
    Then the session config should adhere to the default security rules:
      | refreshTokenValidityHours | 48 |
      | accessTokenValidityHours  | 48 |
