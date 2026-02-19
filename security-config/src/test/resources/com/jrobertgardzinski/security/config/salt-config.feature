Feature: Salt Config

  Scenario: Default config
    Given a new salt configuration is initialized
    When no specific properties are provided
    Then the salt config should adhere to the default security rules:
      | byteLength | 16 |
