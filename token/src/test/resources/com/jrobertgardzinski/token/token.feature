Feature: Token

  Scenario: Random token has a non-blank value
    When a random token is generated
    Then the token value is not blank

  Scenario: Two random tokens are unique
    When two random tokens are generated
    Then the tokens are different

  Scenario: Blank value is rejected
    When a token is created with a blank value
    Then the token creation should be rejected

  Scenario: Access token wraps a base token
    When a random token is generated
    Then it can be wrapped as an access token

  Scenario: Refresh token can check expiration
    Given a refresh token that expired in the past
    When expiration is checked
    Then the token is expired
