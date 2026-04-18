# Collective Allure Test Report & Documentation

Generated on: 2026-04-18 05:42:06

## 📊 Execution Summary

| Module | Total | Passed | Failed | Broken | Skipped | Duration |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| email/email-domain | 23 | 23 | 0 | 0 | 0 | 170ms |
| email/email-security/email-security-config | 2 | 2 | 0 | 0 | 0 | 145ms |
| email/email-security/email-security-system | 39 | 39 | 0 | 0 | 0 | 190ms |
| microservice-security/security-application | 33 | 17 | 0 | 0 | 16 | 48ms |
| microservice-security/security-config | 11 | 11 | 0 | 0 | 0 | 178ms |
| microservice-security/security-domain | 45 | 44 | 1 | 0 | 0 | 232ms |
| password/hash-algorithms/argon2 | 2 | 2 | 0 | 0 | 0 | 663ms |
| password/hash-algorithms/argon2-config | 7 | 7 | 0 | 0 | 0 | 158ms |
| password/password-domain | 2 | 2 | 0 | 0 | 0 | 223ms |
| password/password-security-config | 9 | 9 | 0 | 0 | 0 | 177ms |
| password/password-security-system | 44 | 44 | 0 | 0 | 0 | 188ms |
| **TOTAL** | **217** | **200** | **1** | **0** | **16** | **2.37s** |

## 📝 Test Documentation (Behaviors)

This section describes the verified system behaviors based on passing tests.

### 📦 Epic: Constraints

#### 🎯 Feature: Blocked domain

- ✅ **accepts "user@example.com" (domain not blocked)**
- ✅ **accepts "user@gmail.com" (domain not blocked)**
- ✅ **error code is DOMAIN_BLOCKED**
- ✅ **rejects "admin@junk.org" (domain "junk.org" is blocked)**
- ✅ **rejects "user@spammer.com" (domain "spammer.com" is blocked)**

#### 🎯 Feature: Company domain

- ✅ **accepts "admin@corp.org" (is company domain)**
- ✅ **accepts "user@acme.com" (is company domain)**
- ✅ **accepts any email when no company restriction is configured**
- ✅ **error code is NOT_A_COMPANY_DOMAIN**
- ✅ **rejects "user@example.com" (only "corp.org" is allowed)**
- ✅ **rejects "user@gmail.com" (only "acme.com" is allowed)**

#### 🎯 Feature: Digit

- ✅ **accepts "Password1" (has digit)**
- ✅ **accepts "Secret1#" (has digit)**
- ✅ **accepts "abc123" (has digit)**
- ✅ **error code is DIGIT_REQUIRED**
- ✅ **rejects "NoDigit!" (no digit)**
- ✅ **rejects "Password!" (no digit)**
- ✅ **rejects "Secret#" (no digit)**

#### 🎯 Feature: Disposable email

- ✅ **accepts "user@example.com" (domain not disposable)**
- ✅ **accepts "user@gmail.com" (domain not disposable)**
- ✅ **error code is DISPOSABLE_DOMAIN**
- ✅ **rejects "temp@guerrillamail.com" (domain "guerrillamail.com" is disposable)**
- ✅ **rejects "user@mailinator.com" (domain "mailinator.com" is disposable)**

#### 🎯 Feature: Lowercase

- ✅ **accepts "Password1!" (has lowercase)**
- ✅ **accepts "aBC123" (has lowercase)**
- ✅ **accepts "strong" (has lowercase)**
- ✅ **error code is LOWERCASE_REQUIRED**
- ✅ **rejects "ABC123#" (no lowercase)**
- ✅ **rejects "NOLOWER" (no lowercase)**
- ✅ **rejects "PASSWORD1!" (no lowercase)**

#### 🎯 Feature: MX record

- ✅ **accepts "user@example.com" (has MX record)**
- ✅ **accepts "user@gmail.com" (has MX record)**
- ✅ **error code is NO_MX_RECORD**
- ✅ **rejects "test@nowhere.invalid" (no MX record)**
- ✅ **rejects "user@ghost-domain.com" (no MX record)**

#### 🎯 Feature: Minimum length

- ✅ **accepts "LongPass" (at least 8 chars)**
- ✅ **accepts "Password" (at least 8 chars)**
- ✅ **accepts "Secure1#" (at least 8 chars)**
- ✅ **error code is MIN_LENGTH_NOT_MET**
- ✅ **rejects "Abc1!" (too short)**
- ✅ **rejects "Pa1!" (too short)**
- ✅ **rejects "Sh0rt!" (too short)**

#### 🎯 Feature: RFC format

- ✅ **accepts "j.doe+alias@gmail.com"**
- ✅ **accepts "user123@home.pl"**
- ✅ **accepts "user@example.com"**
- ✅ **error code is RFC_FORMAT_INVALID**
- ✅ **rejects "user test@example.com"**
- ✅ **rejects "user(comment)@example.com"**
- ✅ **rejects "user,extra@example.com"**

#### 🎯 Feature: Special char

- ✅ **accepts "Password1!" (has special char)**
- ✅ **accepts "Secret1@" (has special char)**
- ✅ **accepts "abc1#" (has special char)**
- ✅ **error code is SPECIAL_CHAR_REQUIRED**
- ✅ **rejects "NoSpecial" (no special char)**
- ✅ **rejects "Password1" (no special char)**
- ✅ **rejects "Secret123" (no special char)**

#### 🎯 Feature: Uppercase

- ✅ **accepts "Abc123" (has uppercase)**
- ✅ **accepts "Password1!" (has uppercase)**
- ✅ **accepts "STRONG" (has uppercase)**
- ✅ **error code is UPPERCASE_REQUIRED**
- ✅ **rejects "abc123#" (no uppercase)**
- ✅ **rejects "nouppercase" (no uppercase)**
- ✅ **rejects "password1!" (no uppercase)**

### 📦 Epic: Contract - password verification

#### 🎯 Feature: Hash algorithm

- ✅ **correct password verifies**
   - *Parameters:* algorithm: `Argon2HashAlgorithm`, entered password: `StrongPassword1!`, correct password: `StrongPassword1!`
- ✅ **wrong password does not verify**
   - *Parameters:* algorithm: `Argon2HashAlgorithm`, entered password: `StrongPassword1!23`, correct password: `StrongPassword1!`

### 📦 Epic: Domain

#### 🎯 Feature: PlaintextPassword

- ✅ **Invariant: rejects null**
- ✅ **Security: toString() does not reveal plaintext**
   - *Parameters:* password: `wBnByeUvwWc`, toString(): `PlaintextPassword[value=REDACTED]`, password: `A`, toString(): `PlaintextPassword[value=REDACTED]`, password: `Z`, toString(): `PlaintextPassword[value=REDACTED]`, password: `BjCfUDc`, toString(): `PlaintextPassword[value=REDACTED]`, password: `lETxL`, toString(): `PlaintextPassword[value=REDACTED]`, password: `Ixlfcu`, toString(): `PlaintextPassword[value=REDACTED]`, password: `a`, toString(): `PlaintextPassword[value=REDACTED]`, password: `ebE`, toString(): `PlaintextPassword[value=REDACTED]`, password: `BBMVMxOtylg`, toString(): `PlaintextPassword[value=REDACTED]`, password: `z`, toString(): `PlaintextPassword[value=REDACTED]`, password: `XFDyhIYsZNnhHAWHiWGMQTSqQ`, toString(): `PlaintextPassword[value=REDACTED]`, password: `jiXdwCSUvCN`, toString(): `PlaintextPassword[value=REDACTED]`, password: `AozUgJwUdThHveFTQchDUadpiKlcRqROCAMqUvMZRyqVBbSayOMFotbVUyAIBLOYqhFVwTxAJimastHkJXgR`, toString(): `PlaintextPassword[value=REDACTED]`, password: `oZmNwjQ`, toString(): `PlaintextPassword[value=REDACTED]`, password: `RXmLiS`, toString(): `PlaintextPassword[value=REDACTED]`, password: `CdnJZ`, toString(): `PlaintextPassword[value=REDACTED]`, password: `a`, toString(): `PlaintextPassword[value=REDACTED]`, password: `Z`, toString(): `PlaintextPassword[value=REDACTED]`, password: `FsyanMBkNQZpPJLDANrDTxyzNJbEoDYJXQPkCXEvYSSmThksUikOXNRuCYeakCssLFSMuobkBqjQVJRudGWGdkMcssVxhnaOpmGVnjwbzMhOoOmsLYBLbQiGtpIoAMPLYFwSUIulrJFGuoDhttiBzZLMeCfXQlgHSNogASNVyJARSTCIroxvmPIpNBCiXVqVTmYvooZbIdjdkFZrhuykCQqEtgZBDtIpxwVgWVBeKQkbr`, toString(): `PlaintextPassword[value=REDACTED]`, password: `iZPHEvJp`, toString(): `PlaintextPassword[value=REDACTED]`

### 📦 Epic: DomainPart

#### 🎯 Feature: Domain normalization

- ✅ **normalizes "Booking.Co.Uk" to lowercase "booking.co.uk" by default**
- ✅ **normalizes "GMAIL.COM" to lowercase "gmail.com" by default**
- ✅ **normalizes "Gmail.Com" to lowercase "gmail.com" by default**
- ✅ **normalizes "HOME.PL" to lowercase "home.pl" by default**
- ✅ **normalizes "googlemail.com" to lowercase "googlemail.com" by default**

#### 🎯 Feature: General

- ✅ **Invariant: accepts valid values**
   - *Parameters:* simple: `gmail.com`, Polish TLD: `home.pl`, subdomain: `mail.google.com`, compound TLD: `booking.co.uk`
- ✅ **Invariant: rejects invalid input**
   - *Parameters:* null: `null`, empty: ``, no dot: `localhost`

### 📦 Epic: Email

#### 🎯 Feature: Construction

- ✅ **parses "JohnDoe@GMAIL.COM" to "Email{local=JohnDoe, domain=gmail.com}"**
- ✅ **parses "user@gmail.com" to "Email{local=user, domain=gmail.com}"**

#### 🎯 Feature: General

- ✅ **Invariant: accepts valid addresses**
   - *Parameters:* simple: `user@gmail.com`, with alias: `j.doe+spam@gmail.com`, Polish TLD: `user@home.pl`, compound TLD: `user@booking.co.uk`
- ✅ **Invariant: rejects invalid input**
   - *Parameters:* empty: ``, null: `null`, single space: ` `, missing @: `usergmail.com`, multiple @: `user@@gmail.com`, empty local: `@gmail.com`, empty domain: `user@`, domain without dot: `user@localhost`

### 📦 Epic: Hash Algorithms

#### 🎯 Feature: Argon2 Configuration

- ✅ **Builder without values produces defaults**
   - *Parameters:* default: `Argon2Config[iterations=Iterations[value=3], memLimit=MemLimitInKB[value=65536], parallelism=Parallelism[value=1]]`

#### 🎯 Feature: Argon2 Configuration - Iterations

- ✅ **Invariant: accepts valid values**
   - *Parameters:* MIN: `1`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* MIN - 1: `0`

#### 🎯 Feature: Argon2 Configuration - MemLimitInKB

- ✅ **Invariant: accepts valid values**
   - *Parameters:* MIN: `8`, MAX: `4194304`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* MIN - 1: `7`, MAX + 1: `4194305`

#### 🎯 Feature: Argon2 Configuration - Parallelism

- ✅ **Invariant: accepts valid values**
   - *Parameters:* MIN: `1`, MAX: `16`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* MIN - 1: `0`, MAX + 1: `17`

### 📦 Epic: LocalPart

#### 🎯 Feature: General

- ✅ **Invariant: accepts valid values**
   - *Parameters:* plain: `user`, with dot: `j.doe`, with alias: `j.doe+spam`, alphanumeric: `user123`
- ✅ **Invariant: rejects invalid input**
   - *Parameters:* null: `null`, empty: ``, leading dot: `.john`, trailing dot: `john.`

#### 🎯 Feature: Gmail normalization

- ✅ **normalizes "J.Doe+spam" at gmail.com to "jdoe"**
- ✅ **normalizes "USER+work" at gmail.com to "user"**
- ✅ **normalizes "j.d.o.e" at gmail.com to "jdoe"**
- ✅ **normalizes "plain" at gmail.com to "plain"**

#### 🎯 Feature: Outlook normalization

- ✅ **normalizes "J.Doe" at outlook.com to "j.doe"**
- ✅ **normalizes "J.Doe+alias" at outlook.com to "j.doe"**

#### 🎯 Feature: Yahoo normalization

- ✅ **normalizes "J.Doe-lists" at yahoo.com to "j.doe"**
- ✅ **normalizes "USER" at yahoo.com to "user"**

#### 🎯 Feature: iCloud normalization

- ✅ **normalizes "J.Doe" at icloud.com to "j.doe"**
- ✅ **normalizes "USER" at icloud.com to "user"**

### 📦 Epic: Other

#### 🎯 Feature: General

- ✅ **any null argument → IllegalArgumentException**
   - *Parameters:* null fields: `[blockedDomains]`, null fields: `[disposableDomains]`, null fields: `[companyDomains]`, null fields: `[blockedDomains, disposableDomains]`, null fields: `[blockedDomains, companyDomains]`, null fields: `[disposableDomains, companyDomains]`, null fields: `[blockedDomains, disposableDomains, companyDomains]`
- ✅ **default config → all domain sets are empty**
   - *Parameters:* default: `CanRegisterConfig[blockedDomains=BlockedDomains[values=[]], disposableDomains=DisposableDomains[values=[]], companyDomains=CompanyDomains[values=[]]]`
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [1] "abc"**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **failingRegex(String) [2] some-random_string text**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **notNull()**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [1] user@gmail.com**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**
- ✅ **success(String) [2] andrzej@wp.pl**

#### 🎯 Feature: email

- ✅ ****
- ✅ ****

##### 📖 Story: A typical e-mail

- ✅ **A typical e-mail**

##### 📖 Story: The shortest e-mail possible

- ✅ **The shortest e-mail possible**

#### 🎯 Feature: plaintext password

- ✅ ****
- ✅ ****

#### 🎯 Feature: strong password policy

- ✅ ****

##### 📖 Story: a digit

- ✅ **a digit**
   - *Parameters:* requirement: `a digit`, password: `StrongPassword##`, violation: `DIGIT_REQUIRED`

##### 📖 Story: a lowercase letter

- ✅ **a lowercase letter**
   - *Parameters:* requirement: `a lowercase letter`, password: `STRONGPASSWORD1#`, violation: `LOWERCASE_REQUIRED`

##### 📖 Story: a special character

- ✅ **a special character**
   - *Parameters:* requirement: `a special character`, password: `StrongPassword12`, violation: `SPECIAL_CHAR_REQUIRED`

##### 📖 Story: abc violates 4

- ✅ **abc violates 4**
   - *Parameters:* step: `abc violates 4`, password: `abc`, count: `4`

##### 📖 Story: an uppercase letter

- ✅ **an uppercase letter**
   - *Parameters:* requirement: `an uppercase letter`, password: `strongpassword1#`, violation: `UPPERCASE_REQUIRED`

##### 📖 Story: fix digit

- ✅ **fix digit**
   - *Parameters:* step: `fix digit`, password: `Abcabcabcab1`, count: `1`

##### 📖 Story: fix length

- ✅ **fix length**
   - *Parameters:* step: `fix length`, password: `abcabcabcabc`, count: `3`

##### 📖 Story: fix special char

- ✅ **fix special char**
   - *Parameters:* step: `fix special char`, password: `Abcabcabcab1#`, count: `0`

##### 📖 Story: fix uppercase

- ✅ **fix uppercase**
   - *Parameters:* step: `fix uppercase`, password: `Abcabcabcabc`, count: `2`

##### 📖 Story: minimum length

- ✅ **minimum length**
   - *Parameters:* requirement: `minimum length`, password: `Short1#`, violation: `MIN_LENGTH_NOT_MET`

### 📦 Epic: Password

#### 🎯 Feature: Password Security Configuration - MinLength

- ✅ **Invariant: accepts valid values**
   - *Parameters:* MIN: `5`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* MIN - 1: `4`

#### 🎯 Feature: Password Security Configuration - RequiresDigit

- ✅ **default is true**

#### 🎯 Feature: Password Security Configuration - RequiresLowercase

- ✅ **default is true**

#### 🎯 Feature: Password Security Configuration - RequiresUppercase

- ✅ **default is true**

#### 🎯 Feature: Password Security Configuration - SpecialChars

- ✅ **Default value**
   - *Parameters:* default: `!@#$%^&*`
- ✅ **Invariant: accepts valid values**
   - *Parameters:* value: `".{%_+`, value: `%[!(}`, value: `!`, value: `!@#$%^&*`, value: `!@#$%^&*`, value: `!@#$%^&*`, value: `!`, value: `:;`, value: `!@#$%^&*`, value: `\.!~`
- ✅ **Invariant: rejects duplicate characters**
   - *Parameters:* value: `&&`, value: `\\`, value: `--`, value: `::`, value: `^^`, value: `==`, value: `^^`, value: `$$`, value: `__`, value: `!!`, value: `>>`, value: `==`, value: `::`, value: `{{`, value: `""`, value: `~~`, value: `$$`, value: `~~`, value: `~~`, value: `>>`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* empty string: ``, null: `null`, letter — not a special char: `a`, digit — not a special char: `1`

### 📦 Epic: Password policy

#### 🎯 Feature: General

- ✅ **custom policy retains PROVIDED CONFIGURATION**
   - *Parameters:* minLength: `8`, specialChars: `$%`, requiresUppercase: `false`, requiresLowercase: `true`, requiresDigit: `false`, PROVIDED CONFIGURATION: `PasswordPolicy[minLength=MinLength[value=8], specialChars=SpecialChars[value=$%], requiresUppercase=RequiresUppercase[value=false], requiresLowercase=RequiresLowercase[value=true], requiresDigit=RequiresDigit[value=false]]`
- ✅ **withDefaults() produces DEFAULT CONFIGURATION**
   - *Parameters:* DEFAULT CONFIGURATION: `PasswordPolicy[minLength=MinLength[value=12], specialChars=SpecialChars[value=!@#$%^&*], requiresUppercase=RequiresUppercase[value=true], requiresLowercase=RequiresLowercase[value=true], requiresDigit=RequiresDigit[value=true]]`

### 📦 Epic: Security

#### 🎯 Feature: Security Configuration - AccessTokenValidityHours

- ✅ **Invariant: accepts valid values**
   - *Parameters:* MIN: `1`, NORMAL: `24`, MAX: `2147483647`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* MIN - 1: `0`, NEGATIVE: `-1`, MIN_INT: `-2147483648`

#### 🎯 Feature: Security Configuration - BruteForceConfig

- ✅ **Invariant: rejects failureWindowMinutes < 1**
   - *Parameters:* ZERO: `0`, NEGATIVE: `-1`, MIN_INT: `-2147483648`
- ✅ **Invariant: rejects maxBlockMinutes < minBlockMinutes**
   - *Parameters:* description: `MIN_BLOCK - 1`, minBlockMinutes: `5`, maxBlockMinutes: `4`, description: `MIN_BLOCK vs 1`, minBlockMinutes: `100`, maxBlockMinutes: `1`
- ✅ **Invariant: rejects maxFailures < 1**
   - *Parameters:* ZERO: `0`, NEGATIVE: `-1`, MIN_INT: `-2147483648`
- ✅ **Invariant: rejects minBlockMinutes < 1**
   - *Parameters:* ZERO: `0`, NEGATIVE: `-1`, MIN_INT: `-2147483648`
- ✅ **builder creates config with default values**
   - *Parameters:* defaults: `BruteForceConfig[failureWindowMinutes=15, maxFailures=3, minBlockMinutes=3, maxBlockMinutes=10]`

#### 🎯 Feature: Security Configuration - RefreshTokenValidityHours

- ✅ **Invariant: accepts valid values**
   - *Parameters:* MIN: `1`, NORMAL: `168`, MAX: `2147483647`
- ✅ **Invariant: rejects invalid values**
   - *Parameters:* MIN - 1: `0`, NEGATIVE: `-1`, MIN_INT: `-2147483648`

#### 🎯 Feature: Security Configuration - SessionTokensConfig

- ✅ **Invariant: rejects null arguments**
   - *Parameters:* null fields: `[refreshTokenValidityHours]`, null fields: `[accessTokenValidityHours]`, null fields: `[refreshTokenValidityHours, accessTokenValidityHours]`
- ✅ **stores token validity hours correctly**
   - *Parameters:* refreshTokenValidityHours: `24`, accessTokenValidityHours: `1`

### 📦 Epic: Use case

#### 🎯 Feature: Can Register

- ✅ **Rejects with DISPOSABLE_DOMAIN when "_DisposableEmailConstraint" is unsatisfied**
- ✅ **Rejects with DOMAIN_BLOCKED when "_BlockedDomainConstraint" is unsatisfied**
- ✅ **Rejects with NOT_A_COMPANY_DOMAIN when "_IsEmployeeConstraint" is unsatisfied**
- ✅ **Rejects with RFC_FORMAT_INVALID when "_RfcFormatConstraint" is unsatisfied**
- ✅ **all constraints satisfied → allowed**
- ✅ **all error constraints satisfied, MX absent → allowed with "NO_MX_RECORD" warning**
- ✅ **if any subset of OTHER_CONSTRAINTS is unsatisfied → reject with all their codes**
   - *Parameters:* OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[DOMAIN_BLOCKED, NOT_A_COMPANY_DOMAIN]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[RFC_FORMAT_INVALID]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[RFC_FORMAT_INVALID, DOMAIN_BLOCKED, DISPOSABLE_DOMAIN]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[RFC_FORMAT_INVALID, DOMAIN_BLOCKED]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[NOT_A_COMPANY_DOMAIN]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[RFC_FORMAT_INVALID, NOT_A_COMPANY_DOMAIN]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[NOT_A_COMPANY_DOMAIN, RFC_FORMAT_INVALID, DOMAIN_BLOCKED, DISPOSABLE_DOMAIN]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[DISPOSABLE_DOMAIN, RFC_FORMAT_INVALID, NOT_A_COMPANY_DOMAIN, DOMAIN_BLOCKED]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[NOT_A_COMPANY_DOMAIN, DOMAIN_BLOCKED, DISPOSABLE_DOMAIN]`, OTHER_CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint, _DisposableEmailConstraint, _IsEmployeeConstraint]`, broken constraints: `[DISPOSABLE_DOMAIN]`

#### 🎯 Feature: Can Reset Password

- ✅ **Rejects with DOMAIN_BLOCKED when "_BlockedDomainConstraint" is unsatisfied**
- ✅ **Rejects with RFC_FORMAT_INVALID when "_RfcFormatConstraint" is unsatisfied**
- ✅ **all constraints satisfied → allowed**
- ✅ **if any subset of CONSTRAINTS is unsatisfied → reject with all their codes**
   - *Parameters:* CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint]`, broken constraints: `[RFC_FORMAT_INVALID]`, CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint]`, broken constraints: `[DOMAIN_BLOCKED]`, CONSTRAINTS: `[_RfcFormatConstraint, _BlockedDomainConstraint]`, broken constraints: `[RFC_FORMAT_INVALID, DOMAIN_BLOCKED]`

#### 🎯 Feature: Create Password Hash

- ✅ **Rejects with DIGIT_REQUIRED when "_ContainsDigitConstraint" is unsatisfied**
- ✅ **Rejects with LOWERCASE_REQUIRED when "_ContainsLowercaseConstraint" is unsatisfied**
- ✅ **Rejects with MIN_LENGTH_NOT_MET when "_MinLengthConstraint" is unsatisfied**
- ✅ **Rejects with SPECIAL_CHAR_REQUIRED when "_ContainsSpecialCharConstraint" is unsatisfied**
- ✅ **Rejects with UPPERCASE_REQUIRED when "_ContainsUppercaseConstraint" is unsatisfied**
- ✅ **all constraints satisfied → created**
- ✅ **if any subset of CONSTRAINTS is unsatisfied → rejected with all their codes**
   - *Parameters:* CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[SPECIAL_CHAR_REQUIRED, DIGIT_REQUIRED, LOWERCASE_REQUIRED, MIN_LENGTH_NOT_MET]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[LOWERCASE_REQUIRED, DIGIT_REQUIRED, SPECIAL_CHAR_REQUIRED, MIN_LENGTH_NOT_MET]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[LOWERCASE_REQUIRED, MIN_LENGTH_NOT_MET, SPECIAL_CHAR_REQUIRED, DIGIT_REQUIRED]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[DIGIT_REQUIRED]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[MIN_LENGTH_NOT_MET]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[DIGIT_REQUIRED, LOWERCASE_REQUIRED, SPECIAL_CHAR_REQUIRED]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[MIN_LENGTH_NOT_MET, SPECIAL_CHAR_REQUIRED, LOWERCASE_REQUIRED]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[SPECIAL_CHAR_REQUIRED, DIGIT_REQUIRED, LOWERCASE_REQUIRED, MIN_LENGTH_NOT_MET]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[MIN_LENGTH_NOT_MET]`, CONSTRAINTS: `[_MinLengthConstraint, _ContainsUppercaseConstraint, _ContainsLowercaseConstraint, _ContainsDigitConstraint, _ContainsSpecialCharConstraint]`, broken constraints: `[DIGIT_REQUIRED]`

## ⚠️ Issues (Failed, Broken, or Skipped)

### Module: microservice-security/security-application

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/refresh-session.feature:8`

**Message:**
```
TODO: implement me
```

#### ⏭️ Too many recent failures (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:35`

**Message:**
```
TODO: implement me
```

#### ⏭️ unknown email (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:25`

**Message:**
```
TODO: implement me
```

#### ⏭️ invalid password only (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/register.feature:21`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/refresh-session.feature:15`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:50`

**Message:**
```
TODO: implement me
```

#### ⏭️ wrong password (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:24`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/register.feature:5`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:49`

**Message:**
```
TODO: implement me
```

#### ⏭️ IP is actively blocked (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:30`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/refresh-session.feature:22`

**Message:**
```
TODO: implement me
```

#### ⏭️ wrong email and password (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:26`

**Message:**
```
TODO: implement me
```

#### ⏭️ invalid email and password (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/register.feature:19`

**Message:**
```
TODO: implement me
```

#### ⏭️ invalid email only (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/register.feature:20`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/authenticate.feature:9`

**Message:**
```
TODO: implement me
```

#### ⏭️  (skipped)
**Full Name:** `com/jrobertgardzinski/security/application/register.feature:25`

**Message:**
```
TODO: implement me
```

### Module: microservice-security/security-domain

#### ❌ notNull() (failed)
**Full Name:** `com.jrobertgardzinski.security.domain.vo.EmailTest.notNull`

**Message:**
```
Unexpected exception type thrown, expected: <java.lang.NullPointerException> but was: <java.lang.IllegalArgumentException>
```

