Feature: can register

    Rule: 1. RFC-valid email is accepted

        Example:
        When checking if "user@gmail.com" can register
        Then registration is allowed

    Rule: 2. Disposable email providers are rejected

        Example:
        When checking if "user@mailinator.com" can register
        Then registration is rejected

    Rule: 3. Blocked domains are rejected

        Example:
        When checking if "user@evil.com" can register
        Then registration is rejected
