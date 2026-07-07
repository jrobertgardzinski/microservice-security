@ui
Feature: Registration

  A new USER REGISTERS with an EMAIL and a password. Registration is refused
  when the EMAIL or the password is invalid. An already-taken EMAIL is refused
  quietly: the answer does not reveal that the account exists — the owner of
  the address learns what happened from a mail only they can read.

  Nouns:
    USER  -> User
    EMAIL -> Email
  Verbs:
    REGISTER, REGISTERS, REGISTERED, REGISTRATION -> Register

  Rule: 1. A valid EMAIL and password REGISTER the USER

    Example:
      When the USER REGISTERS with EMAIL "user@example.com" and password "StrongPassword1!"
      Then the USER is REGISTERED

  Rule: 2. An invalid EMAIL or password is rejected, saying which one

    Scenario Outline: <case>
      When the USER REGISTERS with EMAIL "<email>" and password "<password>"
      Then REGISTRATION is rejected
      And the EMAIL is flagged as <email_check>
      And the password is flagged as <password_check>

      Examples:
        | case                      | email          | password         | email_check | password_check |
        | both invalid              | invalid        | weak             | invalid     | invalid        |
        | only the email invalid    | invalid        | StrongPassword1! | invalid     | accepted       |
        | only the password invalid | user@gmail.com | weak             | accepted    | invalid        |

  Rule: 3. An EMAIL that is already REGISTERED is refused quietly, so nobody can probe who has an account

    Example: the same email
      Given the EMAIL "user@example.com" is already REGISTERED
      When the USER REGISTERS with EMAIL "user@example.com" and password "StrongPassword1!"
      Then REGISTRATION is quietly refused, indistinguishable from a fresh one

    Example: a provider alias of that email (Gmail treats dots and "+tags" as the same inbox)
      Given the EMAIL "user@gmail.com" is already REGISTERED
      When the USER REGISTERS with EMAIL "u.s.e.r+promo@gmail.com" and password "StrongPassword1!"
      Then REGISTRATION is quietly refused, indistinguishable from a fresh one
