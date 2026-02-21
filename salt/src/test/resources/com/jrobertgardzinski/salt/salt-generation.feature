Feature: Salt Generation

  Scenario: Generated salt is non-blank
    When a salt is generated with 16 bytes
    Then the salt value is not blank

  Scenario: Two generated salts are unique
    When two salts are generated with 16 bytes
    Then the salts are different

  Scenario: Blank value is rejected
    When a salt is created with a blank value
    Then the salt creation should be rejected
