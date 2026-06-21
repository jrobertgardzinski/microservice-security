Feature: Registration

  A new user registers with an email and a password. Registration is refused
  when the email or the password is invalid, or when the email is already taken.

  Rule: 1. A valid email and password register the user

    Example:
      When the user registers with email "user@example.com" and password "StrongPassword1!"
      Then the user is registered

  Rule: 2. An invalid email or password is rejected, saying which one

    Scenario Outline: <case>
      When the user registers with email "<email>" and password "<password>"
      Then registration is rejected
      And the email is flagged as <email_check>
      And the password is flagged as <password_check>

      Examples:
        | case                      | email          | password         | email_check | password_check |
        | both invalid              | invalid        | weak             | invalid     | invalid        |
        | only the email invalid    | invalid        | StrongPassword1! | invalid     | accepted       |
        | only the password invalid | user@gmail.com | weak             | accepted    | invalid        |

  Rule: 3. An email that is already registered is rejected

    Example:
      Given the email "user@example.com" is already registered
      When the user registers with email "user@example.com" and password "StrongPassword1!"
      Then registration is rejected because the email is already taken
