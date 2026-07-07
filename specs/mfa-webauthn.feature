@ui
Feature: Passkey (WebAuthn) sign-in

  A USER may enrol a PASSKEY — a possession FACTOR proven by a signature, no code to type.
  Once enrolled, the password alone no longer signs in: the authenticator must sign the
  server's challenge. This exercises the same factor chain the e-mail and TOTP factors use,
  proving the factor port is genuinely plug-and-play.

  Nouns:
    USER    -> User
    PASSKEY -> EnrolledFactor
  Verbs:
    AUTHENTICAT* -> Authentication
    ENROL*       -> EnrolFactor

  Background:
    Given a verified USER "passkey@example.com" with password "StrongPassword1!"
    And the USER has a virtual authenticator

  Rule: An enrolled PASSKEY signs the USER in without a typed code

    Example:
      Given the USER has ENROLLED a PASSKEY
      When the USER AUTHENTICATES with the correct password
      Then the USER is signed in by the PASSKEY
