Feature: is employee

    Rule: 1. Email from a company domain identifies an employee

        Example:
        When checking if "jan@acme.com" is an employee
        Then it is confirmed

    Rule: 2. Email from an outside domain is not an employee

        Example:
        When checking if "jan@gmail.com" is an employee
        Then it is not confirmed
