Feature: Password Validation

  Background:
    Given the default password policy is active

  Scenario: Strong password passes
    When the user provides password "Str0ng#Pass!"
    Then the password is accepted

  Scenario: Password too short fails
    When the user provides password "Sh0rt#"
    Then the password is rejected with an error containing "characters long"

  Scenario: Password missing uppercase fails
    When the user provides password "str0ng#pass!"
    Then the password is rejected with an error containing "uppercase"

  Scenario: Password missing digit fails
    When the user provides password "Strong#Pass!"
    Then the password is rejected with an error containing "digit"

  Scenario: Password missing special char fails
    When the user provides password "Str0ngPass1"
    Then the password is rejected with an error containing "special"

  Scenario: Password missing lowercase fails
    When the user provides password "STR0NG#PASS!"
    Then the password is rejected with an error containing "lowercase"
