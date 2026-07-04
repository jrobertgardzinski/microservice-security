Feature: Roles

  Every signed-in USER holds the USER role; an ADMIN may grant MODERATOR or ADMIN on top, and a
  protected who-am-I resource reports the caller's roles so other services can gate on them.

  Nouns:
    USER  -> User
    ROLE* -> Role
  Verbs:
    GRANT* -> SetUserRoles

  Background:
    Given a registered USER "member@example.com" with password "StrongPassword1!"
    And a registered USER "admin@example.com" with password "StrongPassword1!"

  Scenario: a fresh USER is only a USER
    When "member@example.com" asks who they are
    Then their ROLES are exactly "USER"

  Scenario: an ADMIN GRANTS a ROLE and it shows up
    When the ADMIN GRANTS "member@example.com" the ROLES "MODERATOR"
    And "member@example.com" asks who they are
    Then their ROLES are exactly "MODERATOR, USER"

  Scenario: a non-admin cannot GRANT ROLES
    When "member@example.com" tries to GRANT "admin@example.com" the ROLES "ADMIN"
    Then the request is forbidden

  Scenario: GRANTING ROLES to an unknown USER is refused
    When the ADMIN GRANTS "ghost@example.com" the ROLES "MODERATOR"
    Then the request is not found
