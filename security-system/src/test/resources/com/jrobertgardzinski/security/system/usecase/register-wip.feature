@uses-config:email @uses-config:password
Feature: register

  Validity of email and password is defined by configuration, not by this
  feature. Reader looking for what "valid" / "invalid" means should consult:

    - Email configuration (email-security-config)
    - Password configuration (password-security-config)

  This feature verifies only that the registration use case delegates
  validation to those rules and propagates the outcome. Concrete input
  values come from fixtures that read the same configuration under test,
  so there is a single source of truth.

  Rule: 0. Invalid input is rejected before any side effects

    Example:
    When I register with invalid input
    Then registration fails on validating input arguments
    And no account is created

  Rule: 1. A new account is created for valid input

    Example:
    When I register with valid input
    Then registration passes
    And an account is created

  Rule: 2. Registering with an already registered email fails

    Example:
    Given an account is already registered
    When I register with the same email and otherwise valid input
    Then registration fails
    And no new account is created
