Feature: is trusted user

    Rule: 1. Individually whitelisted address is trusted

        Example:
        When checking if "vip@gmail.com" is a trusted user
        Then it is trusted

    Rule: 2. Email from a trusted domain is trusted

        Example:
        When checking if "anyone@partner.com" is a trusted user
        Then it is trusted

    Rule: 3. Unknown address is not trusted

        Example:
        When checking if "stranger@random.com" is a trusted user
        Then it is not trusted
