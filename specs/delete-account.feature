@ui
Feature: Closing the account

  A signed-in USER closes their account. Closure is a SAGA across services: the account locks at
  once (sessions revoked, sign-in refused) and the meme service is asked to purge the USER's
  content — memes in the meme service, comments in the comments service, each axis under its own
  rule (delete / anonymise / keep-popular); votes are retracted. Only the purge confirmation deletes the
  USER for good; no confirmation in time rolls the closure back.

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
      Then the purge command carries that choice

  Rule: The closure completes only when EVERY content service confirmed its purge

    @http-only
    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      When only the meme service confirmed the content purge
      Then the email is not yet free to REGISTER
      When the comments service confirms the content purge too
      And the collections service confirms the content purge too
      Then the USER can REGISTER again with "StrongPassword1!"

  Rule: Without confirmation in time the closure rolls back

    @http-only
    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      When the content purge does not confirm within the time limit
      Then the USER can AUTHENTICATE again with "StrongPassword1!"
