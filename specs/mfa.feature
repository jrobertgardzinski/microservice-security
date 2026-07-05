Feature: Multi-factor sign-in

  A USER may enrol extra FACTORS. Once enrolled, a correct password is no longer
  enough: the sign-in pauses and each FACTOR must be passed, in order, before a
  session is issued. The e-mail FACTOR sends a one-time CODE that must be quoted back.

  Nouns:
    USER   -> User
    FACTOR -> EnrolledFactor
    CODE   -> Challenge
  Verbs:
    AUTHENTICAT* -> Authentication

  Background:
    Given a verified USER "user@example.com" with password "StrongPassword1!"
    And the USER has enrolled the e-mail FACTOR

  Rule: With a FACTOR enrolled, the password alone does not sign in

    Example:
      When the USER AUTHENTICATES with the correct password
      Then a FACTOR CODE is required, not a session

  Rule: Passing the FACTOR completes the sign-in

    Example:
      Given the USER has AUTHENTICATED the password step
      When the USER submits the mailed CODE
      Then the USER is signed in

  Rule: A wrong CODE does not sign in

    Example:
      Given the USER has AUTHENTICATED the password step
      When the USER submits a wrong CODE
      Then the sign-in is refused and a session is not issued
