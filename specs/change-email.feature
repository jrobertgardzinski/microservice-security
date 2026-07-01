Feature: Changing the email address

  A signed-in USER changes their EMAIL by proving ownership of the new one: a verification link goes
  to the new EMAIL, and confirming the token applies the change. Afterwards they AUTHENTICATE under
  the new EMAIL and no longer under the old one. An unknown token is rejected.

  Nouns:
    USER  -> User
    EMAIL -> Email
  Verbs:
    AUTHENTICATE* -> Authentication
    CHANGE*       -> RequestEmailChange
    CONFIRM*      -> ConfirmEmailChange

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: Confirming the token from the link CHANGES the EMAIL

    Example:
      Given the USER has AUTHENTICATED
      When the USER requests to CHANGE the EMAIL to "new@example.com"
      And the USER CONFIRMS the EMAIL CHANGE with the token from the link
      Then the USER can AUTHENTICATE as "new@example.com"
      And the USER cannot AUTHENTICATE as "user@example.com"

  Rule: An unknown token is rejected

    Example:
      When the USER CONFIRMS the EMAIL CHANGE with a garbage token
      Then the EMAIL CHANGE is rejected
