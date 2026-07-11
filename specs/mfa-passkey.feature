@ui
Feature: Passkey sign-in

  A USER may enrol a PASSKEY — a possession FACTOR, nothing to type and nothing to copy.
  Once enrolled, the password alone no longer signs in: the device holding the PASSKEY
  must prove it is present. This exercises the same factor chain the e-mail and TOTP
  factors use, proving the factor port is genuinely plug-and-play. (The protocol behind
  a passkey is an implementation detail and lives in the glue — the argon2 rule.)

  Nouns:
    USER    -> User
    PASSKEY -> EnrolledFactor
  Verbs:
    AUTHENTICAT* -> Authentication
    ENROL*       -> EnrolFactor

  Background:
    Given a verified USER "passkey@example.com" with password "StrongPassword1!"
    And the USER has a device that can hold PASSKEYS

  Rule: An enrolled PASSKEY signs the USER in without a typed code

    Example:
      Given the USER has ENROLLED a PASSKEY
      When the USER AUTHENTICATES with the correct password
      Then the USER is signed in by the PASSKEY
