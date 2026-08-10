# @http-only, not @ui: ROLES ride tokens between services — the browser never sees this
# surface, so the wire glue owns it. Granting has no UI; /me is what other services call.
@http-only
Feature: Granting and reporting ROLES

  Security's duty behind every door in the estate: say who the caller is. The
  who-am-I resource reports the caller's ROLES, so every other service can gate
  on them without guessing — and this is where the ladder the products speak
  starts. A GUEST carries no identity and never reaches security at all; what a
  GUEST may see is each product's own rule. Signing in makes a USER, and every
  USER holds the USER role. MODERATOR and ADMIN are granted on top, only by an
  ADMIN's hand.

  Background:
    Given a registered USER "member@example.com" with password "StrongPassword1!"
    And a registered USER "admin@example.com" with password "StrongPassword1!"

  Rule: A fresh USER is only a USER

    Example:
      When "member@example.com" asks who they are
      Then their ROLES are exactly "USER"

  Rule: An ADMIN grants a ROLE, and from then on it is reported to every service that asks

    Example:
      When the ADMIN GRANTS "member@example.com" the ROLES "MODERATOR"
      And "member@example.com" asks who they are
      Then their ROLES are exactly "MODERATOR, USER"

  Rule: Granting ROLES is an ADMIN's hand alone

    Example:
      When "member@example.com" tries to GRANT "admin@example.com" the ROLES "ADMIN"
      Then the request is forbidden

  Rule: A ROLE cannot be pinned on a USER who does not exist

    Example:
      When the ADMIN GRANTS "ghost@example.com" the ROLES "MODERATOR"
      Then the request is not found
