@ui
Feature: Closing the account

  A signed-in USER closes their account. Closure is a SAGA across services: the account locks at
  once (sessions revoked, sign-in refused) and identity announces the deletion to the PORTAL,
  whose own orchestrator (microservice-offboarding) has every content service purge the USER's
  content — each axis under the USER's chosen rule (delete / anonymise / keep-popular); votes are
  retracted. Identity waits for the portal's single outcome: only "content purged" deletes the
  USER for good; a failed purge — or silence past the safety net — rolls the closure back.

  Nouns:
    USER -> User
  Verbs:
    AUTHENTICATE* -> Authentication
    DELET*        -> StartAccountDeletion
    REGISTER*     -> Register

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: Requesting closure locks the account immediately

    Example:
      Given the USER has AUTHENTICATED
      When the USER requests account DELETION
      Then the access token no longer authorizes
      And the USER cannot AUTHENTICATE with "StrongPassword1!"
      And the email is not yet free to REGISTER

  Rule: The USER chooses what happens to their content

    @http-only
    Example:
      Given the USER has AUTHENTICATED
      When the USER requests account DELETION keeping content with at least 100 votes
      Then the announced deletion carries that choice

  Rule: The closure completes only when the PORTAL confirmed its content purged

    @http-only
    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      Then the email is not yet free to REGISTER
      When the portal confirms the content purge
      Then the USER can REGISTER again with "StrongPassword1!"

  Rule: A failed portal purge rolls the closure back

    @http-only
    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      When the portal reports the content purge failed
      Then the USER can AUTHENTICATE again with "StrongPassword1!"

  Rule: Even total silence rolls the closure back (the safety net)

    @http-only
    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      When no portal outcome arrives within the time limit
      Then the USER can AUTHENTICATE again with "StrongPassword1!"
