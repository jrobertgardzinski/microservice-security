Feature: Closing the account

  A signed-in USER closes their account. Closure is a SAGA across services: the account locks at
  once (sessions revoked, sign-in refused) and the meme service is asked to purge the USER's
  content — their memes disappear with whole comment threads, their comments elsewhere lose the
  author ("deleted account"), their votes are retracted. Only the purge confirmation deletes the
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

  Rule: The meme service's purge confirmation completes the closure

    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      When the meme service confirms the content purge
      Then the USER can REGISTER again with "StrongPassword1!"

  Rule: Without confirmation in time the closure rolls back

    Example:
      Given the USER has AUTHENTICATED
      And the USER requested account DELETION
      When the content purge does not confirm within the time limit
      Then the USER can AUTHENTICATE again with "StrongPassword1!"
