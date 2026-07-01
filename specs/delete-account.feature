Feature: Closing the account

  A signed-in USER closes their account: it is deleted and every session ends, so the USER can no
  longer AUTHENTICATE, the access token no longer authorizes, and the email is free to REGISTER again.

  Nouns:
    USER -> User
  Verbs:
    AUTHENTICATE* -> Authentication
    DELETE*       -> DeleteAccount
    REGISTER*     -> Register

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: Closing the account deletes the USER and ends the sessions

    Example:
      Given the USER has AUTHENTICATED
      When the USER DELETES the account
      Then the access token no longer authorizes
      And the USER cannot AUTHENTICATE with "StrongPassword1!"
      And the USER can REGISTER again with "StrongPassword1!"
