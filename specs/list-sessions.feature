@ui
Feature: Listing active sessions

  A signed-in USER sees their active sessions. Each time they AUTHENTICATE a new session is created,
  and every active one shows up in the list.

  Nouns:
    USER -> User
  Verbs:
    AUTHENTICATE* -> Authentication
    LIST*         -> ListActiveSessions

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: Every active session of the USER is listed

    Example:
      Given the USER has AUTHENTICATED twice
      When the USER LISTS their active sessions
      Then two active sessions are listed
