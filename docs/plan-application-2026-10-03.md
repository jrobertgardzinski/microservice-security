# Plan: `security-application` jako pomost (2026-10-03)

Zaplanowane na polecenie właściciela 2026-10-03. **Plan, nie wykonanie — nic jeszcze nie zmienione.**

## Reguła właściciela (cel, nie propozycja)

- **`application` = pomost** między frameworkiem / klasami Javy a domeną: bierze prymitywy i
  javowe klasy, buduje z nich klasy domenowe, dopiero potem odpala use case'y. Grupuje use case'y
  w serwisy.
- **`infra` = tylko dwie rzeczy:** przypisanie produkcyjnych adapterów do portów i zmapowanie
  `application` na klasy/adnotacje frameworka (beany). **Nic więcej infrze nie potrzebne.**
- **`system`/usecase** czyta `domain` i `config`. Porty (repozytoria) siedzą w `domain`.

Właściciel napisał sam tylko ten serwis i tylko do use case'ów `register` / `authenticate`.
Resztę dopisało AI — i tam jest dryf.

## 0. Werdykty, którym ten plan PRZECZY — wprost

| Gdzie | Co zapisano | Werdykt planu |
|---|---|---|
| `docs/opus-playbook.md:12`, `Readme.md:55-63` (zmienione dziś, `881fb3b`), `todo.md:186,474,795` | „`security-application` NIE MA `src/main` — warstwa testowa" | **Unieważnione** regułą właściciela. Poprawić (krok 7). |
| `docs/review-2026-09-08.md:15` | „no `src/main`" podane agentom jako **konwencja właściciela** | To był wniosek AI z `401c7ed`, nie słowo właściciela. Jedyne słowo właściciela to `uwagi.md:1`: `SecurityService` **zrollbackowany**, bo chciał go mieć. Raport zostaje jako datowany zapis (CLAUDE.md), nie poprawiać. |
| `401c7ed` (cytuje `../BLEDY-2026-08-15.md` B7, `../PRZEGLAD-DDD.md` S10: „martwa fasada") | skasowanie `SecurityService` | Fasada była martwa, bo kontrolery ukradły jej rolę — plan odwraca kierunek. |
| `todo.md:858` „3–5 warstw, nie wymyślać nowych" | — | Zgodne: `application` jest już modułem w reaktorze. Portal ma `memes-application`, `comments-application` z `src/main` — ten sam wzór. |

## 1. Kształt `security-application`

- Pakiet `com.jrobertgardzinski.security.application.<feature>`, klasy **bez adnotacji** (jak
  `system`). Jeden serwis = jedna grupa use case'ów; serwis zwraca własny `sealed` wynik.
- **Do application przechodzi:** `String → Email/PlaintextPassword/Source` + `catch
  IllegalArgumentException`; throttle (`SourceThrottle.check`); `TransactionBoundary.execute`;
  decyzje po wyniku use case'u (dziś `SecurityController:113`, `MeController:52`,
  `FactorsController:132`, `DeleteAccountController:151`).
- **W kontrolerze zostaje:** `HttpRequest`/`@Body Map` → prymitywy, `ClientIpResolver` (czyta
  nagłówki — to framework; zwraca `IpAddress`, akceptowalny styk), `RoleGuard`/`StepUpGuard`/`Caller`
  (czytają token z requestu), `switch(outcome) → HttpResponse`, `Refusal`/`MfaBody`/`emailErrors()`
  (kształt JSON), `@ExecuteOn`.
- **`TransactionBoundary`** (`security-infrastructure/.../TransactionBoundary.java`) → przenieść do
  `security-application` (to port aplikacyjny, nie repozytorium; 2 implementacje w infrze zostają;
  16 kontrolerów dostaje import, potem go traci). Alternatywa `shared/unit-of-work` odpada:
  `run(Runnable)` nie zwraca wartości.
- **Beany:** `BeanFactory` dostaje po jednej metodzie `@Singleton XxxService xxxService(...)` — to
  jest dokładnie „mapowanie application na beany".

### `register` — przed / po

```java
// PRZED (infra, SecurityController) — 7 zależności, decyduje i mapuje
SecurityController(Register, RequestEmailVerification, TransactionBoundary,
                   @Named("registration") SourceThrottle, ClientIpResolver,
                   EmailVerificationRepository, RegistrationNoticeNotifier)
HttpResponse<Map> register(HttpRequest<?> request, @Body Map<String,String> body)

// PO (application)
public final class RegistrationService {
    RegistrationService(Register, RequestEmailVerification, EmailVerificationRepository,
                        RegistrationNoticeNotifier, SourceThrottle, TransactionBoundary)
    public Outcome register(String email, String password, IpAddress source)
    sealed interface Outcome {
        record Registered();
        record QuietlyRefused();            // oba → 201, mail decyduje
        record Rejected(EmailErrorCodes, CanRegisterConfig, PasswordErrorCodes, PasswordPolicy);
        record Throttled(long retryAfterSeconds);
    }
}

// PO (infra)
SecurityController(RegistrationService, ClientIpResolver)
register(request, body) =
    service.register(body.get("email"), body.get("password"), ipResolver.resolve(request))
    → switch → HttpResponse
```

### `authenticate` — przed / po

```java
// PRZED: AuthenticationController(Authentication, ClientIpResolver, RefreshCookies,
//                                 TransactionBoundary, SourceThrottle, Clock)
//        buduje Source (:75, POZA try), throttle, AuthenticationRequest w try/catch → 401 (:85-97)

// PO (application)
public final class AuthenticationService {
    AuthenticationService(Authentication, SourceThrottle, TransactionBoundary)
    public Outcome authenticate(String email, String password, IpAddress ip, String userAgent)
    sealed interface Outcome {
        Authenticated(SessionTokens); Rejected(); EmailNotVerified();
        Blocked(LocalDateTime expiry);
        MfaRequired(String ticket, FactorType next, String challengeData);
        Throttled(long);
    }
}

// PO (infra): AuthenticationController(AuthenticationService, ClientIpResolver, RefreshCookies, Clock)
//             — tylko switch + cookie + Retry-After
```

## 2. Kroki, koszt, dowód

Dowód zawsze przez `./mvnw -o`, **nigdy `install`** (`~/.m2` współdzielone).

| # | Krok | Koszt | Dowód |
|---|---|---|---|
| 0 | Gałąź; baseline `./mvnw -o test` całego reaktora (2 znane czerwone pominąć) | 0,5 h | liczby testów zapisane |
| 1 | **Rejestracja**: `RegistrationService` + `Outcome`; `TransactionBoundary` → application; pomy (p.3); `BeanFactory#registrationService`; `SecurityController` chudnie; `RegistrationServiceTest` na fejkach z test-jara domain (verified→notice, unverified→link, fresh→link, throttled) | 0,5–1 dnia | `-pl security-application,security-infrastructure -am test -Dtest='RunHttpCucumberTest,RegisterEnumerationHttpTest,RegisterThrottleHttpTest,MalformedEmailHttpTest,EdgeErrorsHttpTest,RegistrationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`, potem `-pl security-application,security-infrastructure -am verify` (`analyze-only` z `failOnWarning=true` siedzi w `verify`) |
| 2 | **Authenticate**: `AuthenticationService.authenticate` (bez `/authenticate/factor` — to MFA, krok 4) | 0,5 dnia | `RunHttpAuthenticateTest, AuthThrottleHttpTest, MalformedEmailHttpTest, JwtAccessTokenHttpTest, MfaHttpTest, TrustedProxyHttpTest` (minus znany czerwony) |
| — | **STOP — właściciel ogląda kształt** 2 serwisów / 2 kontrolerów | — | — |
| 3 | Sesje, konto, poczta: `SessionService` (Refresh/Logout/Sessions), `VerificationService`, `PasswordResetService`, `AccountService` (ChangePassword/EmailChange/Confirm), `IdentityService` (`MeController:52`, `UsersController`) — 10 kontrolerów | 2 dni | odpowiednie `RunHttp*Test` + `AddressCaseHttpTest, ResetForStrangersHttpTest, ChangePasswordThrottleHttpTest, DisplayNamesHttpTest` |
| 4 | MFA: `MfaService` (AuthFactor/Factors/RecoveryCodes/AdminFactors), `StepUpService` — 5 kontrolerów; `FactorsController:132` → wynik `WouldBreakFloor` | 1,5 dnia | `MfaHttpTest, StepUpHttpTest, StepUpThrottleHttpTest, MfaRoleFloorHttpTest, AdminFactorResetHttpTest` |
| 5 | Admin/delete/OAuth: `AdminService` (Roles/Settings/PasswordPolicy), `AccountDeletionService.start` (`:151` → `NoSuchUser`), `FederationService` — 5 kontrolerów. `JwksController`, `TestMailboxController` zostają czysto infra (bez use case'u) | 1 dzień | `RunHttpRolesTest, RunHttpSettingsTest, RunHttpPasswordPolicyTest, RunHttpDeleteAccountTest, OauthFlowHttpTest` |
| 6 | **`AccountDeletionOrchestrator`** — p.4 | 1,5–2 dni | `SecurityEventPacts` + weryfikacje paktów (**kształt JSON faktu musi zostać bajt w bajt**), `StaleOutcomeSettlesItsOwnSagaTest, AccountDeletionLoggingTest, OffboardingOutcome*Test, AccountDeletionTimeoutsTest, RunHttpDeleteAccountTest`, suity Testcontainers (Docker) |
| 7 | Docs: `Readme.md:55-63` (łańcuch `UI → Infra → Application → System → Config → Domain`, bez „no src/main"), `docs/opus-playbook.md:12`, `todo.md` — datowane sprostowanie, nie przepisywać starych linii | 0,5 h | `ReadmeCountsTest` (liczy tylko „N at the application layer" — bez zmian) |
| 8 | **Osobny refaktor, PO wszystkim** — p.5 | 0,5 dnia | `-pl security-system,security-application,security-infrastructure -am test` |

**Razem ~7–8 dni roboczych.** Kroki 1–2 (próbka dla właściciela) = **~1,5 dnia**.

## 3. Pomy i to, co się złamie

- `security-infrastructure/pom.xml`: **nowa** zależność `security-application` (compile). Reaktor
  już ma kolejność domain → config → system → application → infrastructure. `Dockerfile` kopiuje
  `target/lib/` z `copy-dependencies` — jar application wejdzie sam.
- `security-application/pom.xml`: `provided` → `compile` dla `security-domain`, `security-system`,
  `email-domain`, `password-domain`; **dodać** `email-config`, `password-usecase` (w `Rejected` są
  `CanRegisterConfig`, `PasswordPolicy`). `dependency:analyze` z `failOnWarning` wymusi dokładność
  i zgłosi w infrze „unused declared", jeśli jakiś moduł przestanie być importowany bezpośrednio —
  wtedy usunąć.
- Test-jary: application konsumuje test-jary domain i system (bez zmian); **nikt** nie konsumuje
  test-jara application; infra nie buduje test-jara. Nic nie pęka.
- **Pułapka `~/.m2`:** leży tam stary, pusty `security-application-1.0.0-SNAPSHOT.jar`.
  `-pl security-infrastructure` **bez `-am`** weźmie go i poleci `NoClassDefFound`. Zawsze `-am`.
- `EdgeErrors.BadInput` (`IllegalArgumentException → 400`) zostaje jako siatka bezpieczeństwa;
  serwisy łapią wcześniej — kontrakt HTTP bez zmian (`EdgeErrorsHttpTest` pilnuje).
- Żaden test nie robi `new XxxController(...)` — zmiana konstruktorów jest bezpieczna.
- Glue Cucumbera w `security-application/src/test` nadal woła use case'y bezpośrednio
  (`RegisterSteps`) — **nie przepinać teraz**; `SpecLiteralsAreRebuildSamplesTest` skanuje tylko
  `src/test/.../feature`.
- **Znane czerwone, niezależne od zmian** (udowodnione 2026-10-03 na worktree sprzed zmian):
  `CorsPreflightTest.an_unknown_origin_is_refused`,
  `TrustedProxyHttpTest.distinct_clients_are_distinct_sources`. Nie liczyć jako regresji.

## 4. `AccountDeletionOrchestrator` — rekomendacja: rozciąć na trzy

245 linii w `security-infrastructure`. Formalnie `implements ContentPurge`, ale to saga: kworum,
zatrzask, retry, timeout, dopisywanie do outboxa, woła `DeleteAccount`, czyta `UserRepository`.
Pod regułą właściciela to nie jest infra.

- **Maszyna stanów** (`begin`/`completePurge`/`compensate`/`compensateOverdue`: zatrzask,
  idempotencja, `lastSagaWasCompensated`, lock `pending_deletion`, który mail) →
  `security-system/account/AccountDeletionSaga`. To orkiestracja use case'ów nad portami — ta sama
  półka co `Authentication` + `MfaChain` (`todo.md:795` sam to mówi). **Nie `application`:** nie
  mapuje prymitywów.
- **Porty do `security-domain`:** `AccountDeletionSagaStore` (dziś interfejs w
  `infra/persistence`, już framework-free — przenieść jak jest) + nowy
  `ClosureAnnouncer { announce(AccountClosure, UUID sagaId, UserId); goodbye(Email); apology(Email) }`.
  Infra: adapter nad `OutboxAppender` + `JsonMapper` (topiki, JSON — to drut).
  `purgeTimeout`/`awaitPortalPurge` → rekord `AccountDeletionConfig` w `security-config` (wzór
  `ChallengeCodeConfig`).
- **Pomost** `AccountDeletionService` w application: `String email, UUID sagaId` → `Email`; woła
  sagę. `OffboardingOutcomeListener` (Kafka) i `AccountDeletionTimeouts` (`@Scheduled`) zostają w
  infrze i wołają serwis.
- `ContentPurge` implementuje saga (system może implementować port domeny — jak dziś
  `AuthenticationFactory`). `SettledDeletionSagaReaper` zostaje w infrze.
- **Ostatni krok (6):** najwyższe ryzyko (pakt, Kafka, Testcontainers), zero zależności od kroków 1–5.

**Reapery** (`AbandonedEmailChangeReaper`, `AbandonedVerificationReaper`): **nie ruszać.** Ich
javadoc wprost: „expiry is a decision and belongs in one place (use case); this only sweeps what
could not matter any more" — to higiena wierszy JDBC pod `@Requires(DataSource)`, nie polityka.

## 5. Porty z `system` do `domain` — PO wszystkim, osobny refaktor (krok 8)

- Realny zakres: **7 portów `mfa`** (`CodeHasher`, `RecoveryCodeHasher`,
  `PendingAuthenticationStore`, `EnrolmentChallengeStore`, `SessionElevation`, `SpentTotpSteps`,
  `StepUpStore`) + 4 rekordy, które niosą (`PendingAuthentication`, `PendingEnrolment`,
  `StepUpPending`, `Challenge` — żaden nie importuje nic z `system`, więc `MfaChain` nie idzie za
  nimi) + 2 fejki z test-jara system (`FakePendingAuthenticationStore`,
  `FakeEnrolmentChallengeStore`) → test-jar domain, wzór z `a60e935`/`5454227`.
- Pozostałe 5 **zostają**: `AuthenticationFactory` to fabryka, nie port; `BlockDurationPolicy`,
  `RolesOf`, `SettingsRepository`, `SettingCatalog` mają implementacje w `system`/`BeanFactory`,
  nie adaptery.
- Pomów nie dotyka (`security-infrastructure` i `security-system` już deklarują `security-domain`
  w scope `compile`). Dotyka za to tych samych 10 miejsc w `BeanFactory` co kroki 1–5 (linie 406,
  419, 481, 492, 510, 532, 668, 669, 753) → robione razem zamieniłoby diffy kontrolerów w szum.
  Osobny commit, mechaniczny.
- Przy przenoszeniu `CodeHasher` zawęzić jego javadoc: w infrastrukturze zostaje **implementacja**,
  deklaracja portu schodzi do domeny. Inaczej plik sam sobie zaprzecza.

## Pliki krytyczne dla wykonania

- `security-infrastructure/src/main/java/com/jrobertgardzinski/SecurityController.java`
- `security-infrastructure/src/main/java/com/jrobertgardzinski/AuthenticationController.java`
- `security-infrastructure/src/main/java/com/jrobertgardzinski/BeanFactory.java`
- `security-application/pom.xml` + `security-infrastructure/pom.xml`
- `security-infrastructure/src/main/java/com/jrobertgardzinski/AccountDeletionOrchestrator.java`
- `security-infrastructure/src/main/java/com/jrobertgardzinski/TransactionBoundary.java`

## Stan faktyczny, zmierzony 2026-10-03 (nie wyprowadzać od nowa)

- `security-application` ma **tylko `src/test`**; pom wyłącznie `provided`/`test`;
  `security-infrastructure` ma **zero** odwołań do `security-application`.
- `security-infrastructure`: **148 plików**, **24 kontrolery**, **51 klas implementujących**
  interfejs (adaptery).
- Porty: **17 w `security-domain`**. W `security-system` 23 interfejsy, z czego **11 to
  zapieczętowane typy wyniku `*Result`** (nie porty — ich miejsce jest tam). Z pozostałych 12
  **dokładnie 7 ma produkcyjny adapter w infrze i wszystkie leżą w `mfa`**.
- `mfa` nie importuje nic z innych pakietów `system`. Tylko `authentication` sięga do `mfa`:
  6 importów, 3 typy (`PendingAuthenticationStore` ×3, `PendingAuthentication` ×2,
  `FactorRegistry` ×1) — po kroku 8 zostają 2 typy, 3 linie.
- `security-domain`: 61 publicznych typów. `security-system`: 63, z czego `mfa` 20.
- Adaptery to package-private klasy `@Singleton` w pakiecie `com.jrobertgardzinski`, wstrzykiwane
  **po typie** — przeniesienie portu o moduł niżej nie dotyka wiązania, zmieniają się tylko importy.
- **Podpowiedzi IDE: przeniesienie portów nie pomoże.** 61 + 63 = 73 + 51 = 124; liczba typów na
  classpathcie jest zachowana. Zmierzone osobno: 283 publiczne typy w pięciu serwisach, tylko 23
  nieużywane poza własnym pakietem (18 z nich w infrze) — na package-private nie ma już czego
  wziąć. Jedyny strukturalny ruch, który zwęża uzupełnienia, to wydzielenie `mfa` jako modułu
  (~98 zamiast 136 typów przy edycji w `mfa`); w `infrastructure` nie poprawi się nigdy, bo jest
  korzeniem kompozycji.
