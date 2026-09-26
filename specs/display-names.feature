Feature: Showing who a person is, by their id

  Other parts of the portal hold a person's id, never their address. When they need to show who
  posted something, they ask security for the names behind a batch of ids.

  Background:
    Given an account for "alice@example.com"
    And an account for "bob@example.com"

  Rule: 1. A name is the address with its local part masked

    Example:
      When the names behind the ids of "alice@example.com" and "bob@example.com" are looked up
      Then "alice@example.com" is shown as "a***@example.com"
      And "bob@example.com" is shown as "b***@example.com"

  Rule: 2. An id nobody holds is absent, not an error

    A deleted account and an invented id look the same from outside: nothing.

    Example:
      When the names behind the id of "alice@example.com" and an id nobody holds are looked up
      Then "alice@example.com" is shown as "a***@example.com"
      And the id nobody holds is not in the answer
