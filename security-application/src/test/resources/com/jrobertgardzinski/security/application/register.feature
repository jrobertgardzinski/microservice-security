Feature: register

    Rule: 1. Anyone can register a new user by passing a valid email and password

        Example:
        When I register with email "user@example.com" and password "StrongPassword1!"
        Then registration passes

    Rule: 2. Invalid input is rejected before any side effects occur

        Scenario Outline: <case>
        When I register with email "<email>" and password "<password>"
        Then registration fails on input validation
        And there are <email_errors> email errors
        And there are <password_errors> password errors

        Examples:
            | case                       | email          | password         | email_errors | password_errors |
            | invalid email and password | invalid        | weak             | some         | some            |
            | invalid email only         | invalid        | StrongPassword1! | some         | no              |
            | invalid password only      | user@gmail.com | weak             | no           | some            |

    Rule: 3. Registering with an already taken email fails

        Example:
        Given a user with email "user@example.com" is already registered
        When I register with email "user@example.com" and password "StrongPassword1!"
        Then registration fails with email already taken
