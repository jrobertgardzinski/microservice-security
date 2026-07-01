Feature: Resetting a forgotten password

  A USER who forgot their password requests a reset link, then sets a new password with the
  RESET TOKEN from the link. Afterwards they AUTHENTICATE with the new password; the old one no
  longer works. An unknown token is rejected.

  Nouns:
    USER        -> User
    RESET TOKEN -> PasswordResetToken
  Verbs:
    AUTHENTICATE* -> Authentication
    RESET*        -> ResetPassword

  Background:
    Given a registered USER "user@example.com" with password "OldPassword1!"

  Rule: A valid RESET TOKEN sets a new password

    Example:
      Given the USER requested a password RESET
      When the USER RESETS the password to "NewPassword1!" with the RESET TOKEN from the link
      Then the USER can AUTHENTICATE with "NewPassword1!"
      And the USER cannot AUTHENTICATE with "OldPassword1!"

  Rule: An unknown RESET TOKEN is rejected

    Example:
      When the USER RESETS the password to "NewPassword1!" with a garbage RESET TOKEN
      Then the password RESET is rejected
