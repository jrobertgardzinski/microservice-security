@ui
Feature: Revoking all sessions

  A USER can log out everywhere at once: revoking all sessions ends every session the
  USER holds, so afterwards no ACCESS TOKEN authorizes and no REFRESH TOKEN can be REFRESHED.

  Background:
    Given a registered USER "user@example.com" with password "StrongPassword1!"

  Rule: REVOKING all sessions ends every session of the USER

    Example:
      Given the USER has two active sessions
      When the USER REVOKES all sessions
      Then neither ACCESS TOKEN authorizes any longer
      And neither REFRESH TOKEN can be REFRESHED
