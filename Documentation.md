# Collective Allure Test Report & Documentation

Generated on: 2026-06-13 20:31:30

## 📊 Execution Summary

| Module | Total | Passed | Failed | Broken | Skipped | Duration |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| email/email-domain | 13 | 13 | 0 | 0 | 0 | 158ms |
| email/email-security/email-security-config | 1 | 1 | 0 | 0 | 0 | 99ms |
| email/email-security/email-security-system | 28 | 28 | 0 | 0 | 0 | 184ms |
| microservice-security/security-config | 13 | 13 | 0 | 0 | 0 | 168ms |
| microservice-security/security-system | 17 | 17 | 0 | 0 | 0 | 938ms |
| password/hash-algorithms/argon2 | 2 | 2 | 0 | 0 | 0 | 649ms |
| password/hash-algorithms/argon2-config | 7 | 7 | 0 | 0 | 0 | 157ms |
| password/password-domain | 1 | 1 | 0 | 0 | 0 | 184ms |
| password/password-security/password-security-config | 9 | 9 | 0 | 0 | 0 | 172ms |
| password/password-security/password-security-system | 19 | 19 | 0 | 0 | 0 | 184ms |
| **TOTAL** | **110** | **110** | **0** | **0** | **0** | **2.89s** |

## 📝 Test Documentation (Behaviors)

This section describes the verified system behaviors based on passing tests.

### Epic: Email

#### Feature: Config

##### Story: CanRegister

- **default config → all domain wrappers are absent**
  - *Parameters:*
    - default: `CanRegisterConfig[blockedDomains=null, disposableDomains=null, companyDomains=null]`

#### Feature: Constraints

##### Story: BlockedDomain (for instance: "junk.org")

- **accepts "user@example.com"**
- **error code is DOMAIN_BLOCKED**
- **rejects "admin@junk.org" (domain is on the blocked list)**

##### Story: CompanyDomain (for instance: "corp.org")

- **accepts "user@corp.org"**
- **error code is NOT_A_COMPANY_DOMAIN**
- **rejects "somebody@public-vendor.com" (domain is not on the company list)**

##### Story: DisposableEmail (for instance: "mailinator.com")

- **accepts "user@example.com"**
- **error code is DISPOSABLE_DOMAIN**
- **rejects "somebody@mailinator.com" (domain is on the disposable list)**

##### Story: MX record (for instance: fails on "no-mx.com" domain)

- **accepts "user@anything-else.com"**
- **error code is NO_MX_RECORD**
- **rejects "somebody@no-mx.com"**

##### Story: RFC format

- **accepts "user@example.com"**
- **error code is RFC_FORMAT_INVALID**
- **rejects "user test@example.com"**
- **rejects "user(comment)@example.com"**
- **rejects "user,extra@example.com"**

#### Feature: Domain

##### Story: DomainPart

- **Invariant: accepts valid values**
  - *Parameters:*
    - simple: `gmail.com`
    - Polish TLD: `home.pl`
    - subdomain: `mail.google.com`
    - compound TLD: `booking.co.uk`
- **Invariant: rejects invalid input**
  - *Parameters:*
    - no dot: `localhost`
- **normalizesToLowercaseByDefault**
  - *Parameters:*
    - normalizes: `GMAIL.COM -> gmail.com`
    - normalizes: `Gmail.Com -> gmail.com`
    - normalizes: `googlemail.com -> googlemail.com`
    - normalizes: `HOME.PL -> home.pl`
    - normalizes: `Booking.Co.Uk -> booking.co.uk`

##### Story: Email

- **Invariant: accepts valid addresses**
  - *Parameters:*
    - simple: `user@gmail.com`
    - with alias: `j.doe+spam@gmail.com`
    - Polish TLD: `user@home.pl`
    - compound TLD: `user@booking.co.uk`
- **Invariant: rejects invalid input**
  - *Parameters:*
    - single space: ` `
    - missing @: `usergmail.com`
    - multiple @: `user@@gmail.com`
    - empty local: `@gmail.com`
    - empty domain: `user@`
    - domain without dot: `user@localhost`
- **parses "JohnDoe@GMAIL.COM" to "Email{local=JohnDoe, domain=gmail.com}"**
- **parses "user@gmail.com" to "Email{local=user, domain=gmail.com}"**

##### Story: LocalPart

- **Gmail normalization**
  - *Parameters:*
    - normalizes: `J.Doe+spam -> jdoe`
    - normalizes: `j.d.o.e -> jdoe`
    - normalizes: `JDOE+work -> jdoe`
- **Invariant: accepts valid values**
  - *Parameters:*
    - plain: `user`
    - with dot: `j.doe`
    - with alias: `j.doe+spam`
    - alphanumeric: `user123`
- **Invariant: rejects invalid input**
  - *Parameters:*
    - leading dot: `.john`
    - trailing dot: `john.`
- **Outlook normalization**
  - *Parameters:*
    - normalizes: `J.Doe+alias -> j.doe`
    - normalizes: `J.Doe -> j.doe`
- **Yahoo normalization**
  - *Parameters:*
    - normalizes: `J.Doe-lists -> j.doe`
    - normalizes: `USER -> user`
- **iCloud normalization**
  - *Parameters:*
    - normalizes: `J.Doe -> j.doe`
    - normalizes: `USER -> user`

#### Feature: Use case

##### Story: CanRegister

- **Rejects with DISPOSABLE_DOMAIN when "_DisposableEmailConstraint" is unsatisfied**
- **Rejects with DOMAIN_BLOCKED when "_BlockedDomainConstraint" is unsatisfied**
- **Rejects with NOT_A_COMPANY_DOMAIN when "_IsEmployeeConstraint" is unsatisfied**
- **Rejects with RFC_FORMAT_INVALID when "_RfcFormatConstraint" is unsatisfied**
- **all constraints satisfied → allowed**
- **all error constraints satisfied, MX absent → allowed with "NO_MX_RECORD" warning**
- **if any subset of OTHER_CONSTRAINTS is unsatisfied → reject with all their codes**
  - *Parameters:*
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[NOT_A_COMPANY_DOMAIN]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[NOT_A_COMPANY_DOMAIN]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[DISPOSABLE_DOMAIN, RFC_FORMAT_INVALID, NOT_A_COMPANY_DOMAIN, DOMAIN_BLOCKED]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[NOT_A_COMPANY_DOMAIN, DOMAIN_BLOCKED]`
    - OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID, NOT_A_COMPANY_DOMAIN, DOMAIN_BLOCKED]`

##### Story: CanResetPassword

- **Rejects with DOMAIN_BLOCKED when "_BlockedDomainConstraint" is unsatisfied**
- **Rejects with RFC_FORMAT_INVALID when "_RfcFormatConstraint" is unsatisfied**
- **all constraints satisfied → allowed**
- **if any subset of CONSTRAINTS is unsatisfied → reject with all their codes**
  - *Parameters:*
    - CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID]`
    - CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint]`
    - broken constraints: `[DOMAIN_BLOCKED]`
    - CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint]`
    - broken constraints: `[RFC_FORMAT_INVALID, DOMAIN_BLOCKED]`

### Epic: Hash Algorithm: Argon2

#### Feature: Configuration

- **Builder without values produces defaults**
  - *Parameters:*
    - default: `Argon2Config[iterations=Iterations[value=3], memLimit=MemLimitInKB[value=65536], parallelism=Parallelism[value=1]]`

##### Story: Iterations

- **Invariant: accepts valid values**
  - *Parameters:*
    - MIN: `1`
- **Invariant: rejects invalid values**
  - *Parameters:*
    - MIN - 1: `0`

##### Story: MemLimitInKB

- **Invariant: accepts valid values**
  - *Parameters:*
    - MIN: `8`
    - MAX: `4194304`
- **Invariant: rejects invalid values**
  - *Parameters:*
    - MIN - 1: `7`
    - MAX + 1: `4194305`

##### Story: Parallelism

- **Invariant: accepts valid values**
  - *Parameters:*
    - MIN: `1`
    - MAX: `16`
- **Invariant: rejects invalid values**
  - *Parameters:*
    - MIN - 1: `0`
    - MAX + 1: `17`

#### Feature: Contract

##### Story: password verification

- **correct password verifies**
  - *Parameters:*
    - algorithm: `Argon2HashAlgorithm`
    - entered password: `StrongPassword1!`
    - correct password: `StrongPassword1!`
- **wrong password does not verify**
  - *Parameters:*
    - algorithm: `Argon2HashAlgorithm`
    - entered password: `StrongPassword1!23`
    - correct password: `StrongPassword1!`

### Epic: Password

#### Feature: Constraints

##### Story: Digit

- **accepts "Password1"**
- **error code is DIGIT_REQUIRED**
- **rejects "Password!" (no digit)**

##### Story: Lowercase

- **accepts "Password1!"**
- **error code is LOWERCASE_REQUIRED**
- **rejects "PASSWORD1!" (no lowercase)**

##### Story: Minimum length (for instance: 8 characters long)

- **accepts "LongPass"**
- **error code is MIN_LENGTH_NOT_MET**
- **rejects "Sh0rt!" (at least 8 chars)**

##### Story: Special char (for instance: "!")

- **accepts "Password1!"**
- **error code is SPECIAL_CHAR_REQUIRED**
- **rejects "Password1" (no special char)**

##### Story: Uppercase

- **accepts "Password1!"**
- **error code is UPPERCASE_REQUIRED**
- **rejects "password1!" (no uppercase)**

#### Feature: Domain

##### Story: PlaintextPassword

- **Security: toString() does not reveal plaintext**
  - *Parameters:*
    - password: `AAA`
    - toString(): `PlaintextPassword[value=REDACTED]`
    - password: `DBRirGSFIFwYW`
    - toString(): `PlaintextPassword[value=REDACTED]`
    - password: `bSJt`
    - toString(): `PlaintextPassword[value=REDACTED]`

#### Feature: Password Security Configuration

##### Story: MinLength

- **accepts**
  - *Parameters:*
    - MIN: `5`
- **rejects**
  - *Parameters:*
    - MIN - 1: `4`

##### Story: RequiresDigit

- **default is true**

##### Story: RequiresLowercase

- **default is true**

##### Story: RequiresUppercase

- **default is true**

##### Story: SpecialChars

- **Default value**
  - *Parameters:*
    - default: `!@#$%^&*`
- **Invariant: accepts valid values**
  - *Parameters:*
    - value: `!`
    - value: `!@#$%^&*`
    - value: `!@#$%^&*`
    - value: `!@#$%^&*`
    - value: `!@#$%^&*`
    - value: `!@#$%^&*`
    - value: `?!|[_`
    - value: `?"!,=/.~`
    - value: `!`
    - value: `!@#$%^&*`
- **Invariant: rejects duplicate characters**
  - *Parameters:*
    - value: `>>`
    - value: `!!`
    - value: `!!`
    - value: `**`
    - value: `$$`
    - value: `{{`
    - value: `//`
    - value: `{{`
    - value: `==`
    - value: `--`
    - value: `~~`
    - value: `++`
    - value: `!!`
    - value: `##`
    - value: `$$`
    - value: `~~`
    - value: `''`
    - value: `&&`
    - value: `\\`
    - value: `**`
- **Invariant: rejects invalid values**
  - *Parameters:*
    - letter — not a special char: `a`
    - digit — not a special char: `1`

#### Feature: Password policy

##### Story: Config

- **custom policy retains PROVIDED CONFIGURATION**
  - *Parameters:*
    - minLength: `8`
    - specialChars: `$%`
    - requiresUppercase: `false`
    - requiresLowercase: `true`
    - requiresDigit: `false`
    - PROVIDED CONFIGURATION: `PasswordPolicy[minLength=MinLength[value=8], specialChars=SpecialChars[value=$%], requiresUppercase=RequiresUppercase[value=false], requiresLowercase=RequiresLowercase[value=true], requiresDigit=RequiresDigit[value=false]]`
- **withDefaults() produces DEFAULT CONFIGURATION**
  - *Parameters:*
    - DEFAULT CONFIGURATION: `PasswordPolicy[minLength=MinLength[value=5], specialChars=SpecialChars[value=!@#$%^&*], requiresUppercase=RequiresUppercase[value=true], requiresLowercase=RequiresLowercase[value=true], requiresDigit=RequiresDigit[value=true]]`

#### Feature: Use case

##### Story: Create Password Hash

- **all constraints satisfied → allowed**
- **if any subset of constraints is unsatisfied → rejected with all their codes**
  - *Parameters:*
    - broken constraints: `[MIN_LENGTH_NOT_MET]`
    - broken constraints: `[SPECIAL_CHAR_REQUIRED]`
    - broken constraints: `[SPECIAL_CHAR_REQUIRED]`
    - broken constraints: `[MIN_LENGTH_NOT_MET, DIGIT_REQUIRED, SPECIAL_CHAR_REQUIRED]`
    - broken constraints: `[SPECIAL_CHAR_REQUIRED]`
    - broken constraints: `[UPPERCASE_REQUIRED, LOWERCASE_REQUIRED, SPECIAL_CHAR_REQUIRED, MIN_LENGTH_NOT_MET, DIGIT_REQUIRED]`
    - broken constraints: `[SPECIAL_CHAR_REQUIRED]`
    - broken constraints: `[MIN_LENGTH_NOT_MET, DIGIT_REQUIRED, UPPERCASE_REQUIRED]`
    - broken constraints: `[LOWERCASE_REQUIRED, SPECIAL_CHAR_REQUIRED, DIGIT_REQUIRED]`
    - broken constraints: `[SPECIAL_CHAR_REQUIRED, UPPERCASE_REQUIRED]`

### Epic: Security

#### Feature: Security Configuration - AccessTokenValidityHours

##### Story: Access Token Validity [hours] config

- **accepts**
  - *Parameters:*
    - MIN: `1`
    - above MIN: `24`
- **rejects**
  - *Parameters:*
    - MIN - 1: `0`
    - negative: `-1`

#### Feature: Security Configuration - BruteForceConfig

- **Default**
  - *Parameters:*
    - default config: `BruteForceConfig[failureWindowMinutes=FailureWindowMinutes[value=15], maxFailures=MaxFailures[value=3], minBlockMinutes=MinBlockMinutes[value=3], maxBlockMinutes=MaxBlockMinutes[value=10]]`

##### Story: Failure Window Minutes Configuration

- **accepts**
  - *Parameters:*
    - MIN: `3`
    - between: `75`
    - MAX: `120`
- **rejects**
  - *Parameters:*
    - Min int: `-2147483648`
    - MIN - 1: `2`
    - MAX + 1: `121`
    - Max int: `2147483647`

##### Story: Max Block Minutes Configuration

- **accepts**
  - *Parameters:*
    - MIN: `1`
    - between: `1072`
    - MAX: `1440`
- **rejects**
  - *Parameters:*
    - Min int: `-2147483648`
    - MIN - 1: `0`
    - MAX + 1: `1441`
    - Max int: `2147483647`

##### Story: Max Failures Configuration

- **accepts**
  - *Parameters:*
    - MIN: `1`
    - between: `5`
    - MAX: `20`
- **rejects**
  - *Parameters:*
    - Min int: `-2147483648`
    - MIN - 1: `0`
    - MAX + 1: `21`
    - Max int: `2147483647`

##### Story: Min Block Minutes Configuration

- **accepts**
  - *Parameters:*
    - MIN: `1`
    - between: `30`
    - MAX: `60`
- **rejects**
  - *Parameters:*
    - Min int: `-2147483648`
    - MIN - 1: `0`
    - MAX + 1: `61`
    - Max int: `2147483647`

#### Feature: Security Configuration - RefreshTokenValidityHours

##### Story: Refresh Token Validity [hours] config

- **accepts**
  - *Parameters:*
    - MIN: `1`
    - above MIN: `168`
- **rejects**
  - *Parameters:*
    - MIN - 1: `0`
    - negative: `-1`

### Epic: Use case

#### Feature: Authentication

- **Blocked when the brute-force guard blocks the IP**
- **Failed when the guard allows but credentials are invalid**
- **Passed when the guard allows and credentials are valid**

##### Story: Brute-force guard

- **Blocked and a new block created when the failure limit is reached**
- **Blocked when an active block already exists for the IP**
- **Passed when there is no active block and failures are below the limit**

##### Story: Clean brute-force records

- **Removes both failed-authentication records and authentication blocks for the IP**

##### Story: Generate session

- **Creates session tokens for the email and returns the persisted result**

##### Story: Update brute-force records

- **Records a failed authentication for the IP stamped with the current time**

##### Story: Verify credentials

- **Failed when no user exists for the email**
- **Failed when the user exists but the password hash does not match**
- **Passed when the user exists and the password hash matches**

#### Feature: Refresh session

- **Expired when refresh token is found but expired**
- **NotFound when no refresh token is stored**
- **Passed when refresh token is found and not expired**

#### Feature: Register

- **Invalid when email, password, or both fail validation**
- **Valid when both email and password pass validation**

