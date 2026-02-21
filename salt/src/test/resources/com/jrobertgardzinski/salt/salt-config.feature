Feature: Salt Config

  Scenario: Default config
    Given a new salt configuration is initialized
    When no specific properties are provided
    Then the salt config should adhere to the default security rules:
      | byteLength | 16 |

  Scenario: Custom byte length
    Given a new salt configuration is initialized
    When the byte length is set to 32
    Then the salt byte length should be 32

  Scenario: Byte length below minimum is rejected
    Given a new salt configuration is initialized
    When the byte length is set to 7
    Then the salt configuration should be rejected
