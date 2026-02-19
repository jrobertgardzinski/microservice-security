Feature: can reset password

    Rule: 1. RFC-valid email can trigger a password reset

        Example:
        When checking if "user@gmail.com" can reset password
        Then password reset is allowed

    Rule: 2. Disposable email providers are also accepted

        Example:
        When checking if "user@mailinator.com" can reset password
        Then password reset is allowed
