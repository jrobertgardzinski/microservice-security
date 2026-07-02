Feature: Verifying an email address

  A USER proves they own their EMAIL by following a verification link sent to it. The link
  carries a single-use VERIFICATION TOKEN; the matching token marks the EMAIL as verified, and
  an unknown token is rejected.

  Nouns:
    USER               -> User
    EMAIL              -> Email
    VERIFICATION TOKEN -> VerificationToken
  Verbs:
    VERIF* -> VerifyEmail

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: The VERIFICATION TOKEN from the link verifies the EMAIL

    Example:
      Given the USER requested EMAIL VERIFICATION
      When the USER VERIFIES the EMAIL with the VERIFICATION TOKEN from the link
      Then the EMAIL is verified

  Rule: An unknown VERIFICATION TOKEN is rejected

    Example:
      When the USER VERIFIES the EMAIL with a garbage VERIFICATION TOKEN
      Then the VERIFICATION is rejected

  Rule: Registration automatically starts VERIFICATION

    Example:
      Then a VERIFICATION link has been e-mailed to the USER
