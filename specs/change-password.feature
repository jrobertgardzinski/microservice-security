@ui
Feature: Changing the password

  A signed-in USER changes their password by proving the current one. Afterwards they AUTHENTICATE
  with the new password; the old one no longer works. A wrong current password is rejected.

  Background:
    Given a registered USER "user@example.com" with password "OldPassword1!"

  Rule: The correct current password lets the USER CHANGE it

    Example:
      Given the USER has AUTHENTICATED
      When the USER CHANGES the password from "OldPassword1!" to "NewPassword1!"
      Then the USER can AUTHENTICATE with "NewPassword1!"
      And the USER cannot AUTHENTICATE with "OldPassword1!"

  Rule: A wrong current password is rejected

    Example:
      Given the USER has AUTHENTICATED
      When the USER CHANGES the password from "WrongPassword1!" to "NewPassword1!"
      Then the password CHANGE is rejected
