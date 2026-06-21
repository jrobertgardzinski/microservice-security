# TODO

## ✅ ZROBIONE 2026-06-21 — swap-safety kodów błędów (email vs password)

Problem: `RegisterResult.Rejected(List<String> emailErrors, List<String> passwordErrors)` —
dwa argumenty tego samego typu → ciche przestawienie email↔password (kompilator nie ostrzega).
Realny strach: „kody błędów email trafią do password".

**Enum ODRZUCONY (świadomie).** Dziś kod błędu = `static final String CODE` PRZY swoim constraintcie
(`_RfcFormatConstraint` ma `RFC_FORMAT_INVALID`, `_IsEmployeeConstraint` ma `NOT_A_COMPANY_DOMAIN` itd.)
— wysoka kohezja, każdy constraint samowystarczalny. Enum CENTRALIZOWAŁBY kody → coupling + każdy
constraint zależny od wspólnego typu. Co gorsza: framework ograniczeń jest CELOWO OTWARTY
(`Constraint.code()` abstrakcyjne, dowolny custom constraint z nowym kodem — patrz anonimowe w testach),
a enum to zbiór ZAMKNIĘTY → walczyłby z rozszerzalnością. Werdykt: String przy constraintcie zostaje.
(Kod błędu to identyfikator transportowy — i18n key / JSON / UI — prymityw jest tu adekwatny, nie ma
inwariantów ani zachowania do owinięcia w VO.)

**Rozwiązanie (wybrana opcja B z rozmowy): osobne typy-KONTENERY per kanał, nie per kod.**
- `EmailErrorCodes` / `PasswordErrorCodes` — finalne klasy (NIE rekordy, bo rekord = publiczny
  kanoniczny ctor = furtka „zbuduj z gołej listy"). Prywatny ctor + pakietowa fabryka `of(Outcome<…>)`.
- `Rejected(EmailErrorCodes, PasswordErrorCodes)` — różne typy pól.
- Trzy spusty zamykające swap: (1) różne typy pól → przestawienie slotów = błąd kompilacji;
  (2) `of(Outcome<Email>)` otypowane → `EmailErrorCodes.of(passwordOutcome)` = błąd kompilacji;
  (3) prywatny ctor → `new EmailErrorCodes(jakasLista)` niedostępne spoza klasy.
  Efekt: cross-slot swap NIEWYRAŻALNY w typach, nie tylko „pilnowany".
- Kody w środku zostają `String` (`.codes()`), zero couplingu do constraintów.
- Pliki: nowe `EmailErrorCodes`/`PasswordErrorCodes` (security-system/registration, public);
  zmienione `RegisterResult`, `RegistrationAttempt` (buduje przez `.of(...)`), `RegisterTest`,
  `RegisterSteps` (czytają `.codes()`). Usunięty `// todo consider dedicated errors...`.
- ✅ BUILD ZWERYFIKOWANY 2026-06-21 (JDK 25, pełny `clean install`): BUILD SUCCESS.
  security-config 13/0, security-system 17/0, security-application (RunCucumberTest, 3 feature'y) 17/0.
  Potwierdza, że swap-safety + `.codes()` w `RegisterSteps` działają end-to-end.
  (security-infrastructure dalej POZA reaktorem — patrz milestone multi-level niżej.)

**Drobny dług zauważony przy okazji (NIE zrobione — obce repo `email`/`password`):** w niektórych
constraintach `code()` zwraca ZDUPLIKOWANY literał zamiast stałej `CODE` (np. `_ContainsDigitConstraint`:
pole `CODE="DIGIT_REQUIRED"`, a `code()` zwraca `"DIGIT_REQUIRED"` literałem). `code()` powinno zwracać
`CODE`. Świadomie NIE ruszone w nocy: to osobne repozytoria git — nie commituję w nich bez Ciebie.
Quick win na 10 min, gdy dasz znać.

## 🔜 PRIORYTET 1 (następny) — jeden feature z 2 poziomów: application + infrastructure HTTP

Ustalone 2026-06-21. To jest dowód głównej tezy z Readme: „same behaviour, different entry point".
Dziś 3 feature'y chodzą TYLKO z warstwy application. Cel milestone'u: ten SAM `.feature` napędzany
także z infry (realne wejście HTTP). NIE kopiujemy Gherkina — jeden plik, dwa zestawy step-defs.

**Czemu `register.feature` jako pierwszy:** brak zegara, brute-force i sesji — czysta walidacja +
zapis. Najłatwiej przepchnąć przez sieć i zobaczyć zielone na drugim poziomie. (authenticate ma zegar
i brute-force, refresh ma stan sesji — trudniejsze, później.)

**Stan startowy (sprawdzone):** w infrze JEST szkielet `SecurityController` (dziś zwraca `"OK"`),
moduł `security-infrastructure` WYKOMENTOWANY z parent pom → poza reaktorem. ⚠️ DLATEGO tego milestone'u
NIE DA SIĘ zweryfikować w obecnym sandboxie, dopóki infra nie wróci do builda. To główny powód, czemu
nie zacząłem go w nocy — nie chcę commitować niezweryfikowanego wejścia HTTP.

**Kroki (do zrobienia z Tobą):**
1. Odkomentować `security-infrastructure` w parent pom; doprowadzić moduł do `BUILD SUCCESS`
   (prod DI: `BeanFactory` + `AuthenticationFactory` + `RandomBlockDurationPolicy` — patrz „Faza 2" niżej).
2. `SecurityController` realnie: `POST /register` {email, password} → `SecurityService.register`
   → odpowiedź HTTP (201/200 Registered, 4xx Rejected z `emailErrors`/`passwordErrors().codes()`).
3. Drugi zestaw step-defs dla TEGO SAMEGO `register.feature`, uderzający po HTTP (MockMvc /
   WebTestClient / realny port) zamiast wołać `SecurityService` wprost.
4. Runner wskazujący ten sam `.feature` z innym glue (osobny pakiet / profil Cucumbera).
   KLUCZ: jeden Gherkin, dwa glue → warstwy nie mogą się rozjechać w zachowaniu (czytają tę samą spec).
5. UI (3. poziom) — ŚWIADOMIE później: wymaga frontu/drivera, dużo plumbingu za mały zysk ponad app+infra.

**Otwarta decyzja w tym milestonie:** jak fizycznie współdzielić `.feature` między dwoma runnerami
(ten sam plik na classpath + dwa `@SelectClasspathResource` z różnym glue-base, vs jeden runner z
dwoma profilami). Do ustalenia na starcie.

## DECYZJE

- ✅ **Nazewnictwo „failure/failed" vs „rejection/rejected" — ROZSTRZYGNIĘTE 2026-06-21 (rozdziel).**
  `failure/failures` ZOSTAJE jako pojęcie brute-force w produkcji (`FailuresCount`, `countFailuresBy`,
  `hasReachedTheLimit` — nietknięte). Nazwy metod testów + `@Label` wyrównane do typu/wariantu, który
  test sprawdza (`passed_`→`authenticated_/valid_/allowed_/refreshed_`, `failed_`→`rejected_/invalid_`),
  + naprawione `@Label` czytające „Authenticated" na testach eventów wewnętrznych. Commit `a323cd5`,
  build zielony (17/0). `failed to authenticate` / `failed attempts` w `.feature` zostawione.
- ✅ **Ochrona rejestracji „already taken" — DOMKNIĘTE 2026-06-21.** `RegistrationAttempt.resolve`
  sprawdza `userRepository.findBy(email)` PO walidacji, PRZED zapisem; przy duplikacie zwraca nowy
  wariant `RegisterResult.EmailAlreadyTaken(email)` zamiast zapisać duplikat. Dodane: Rule 3 w
  `register.feature` + step-defs (`the email "…" is already registered`) + test w `RegisterTest`.
  Commit `3ed375c`, build zielony (RegisterTest 3/3, RunCucumberTest 18/18).
  UWAGI: (1) klucz unikalności = `findBy(Email)` (jak w authentication), NIE `NormalizedEmail` —
  duplikat przez alias (kropki gmaila) wciąż przejdzie; uniknięcie tego = osobny `findByNormalized`,
  do rozważenia. (2) `UserAlreadyExistsEvent` (domena) dalej nieużywany — `RegisterResult` to wynik
  systemu, nie event; świadomie nie wpinałem eventów (Registered/Rejected też nie są eventami).
  (3) Throttling/IP/enumeracja kont przy rejestracji — NADAL otwarte, osobny większy temat (niżej).

## STAN — gdzie jesteśmy (czytaj najpierw)

Pracujemy nad modułem `microservice-security`, wątek: ubiquitous language + czytelność.
- ✅ REVIEW feature'ów (2026-06-17): input-owe ujednolicone na „the user" + sentence-case.
  `strong-password-policy` — kody błędów schowane za język biznesu (mapowanie reason→code w step-defie),
  reguła „fixing one by one" usunięta (pokrycie w `CreatePasswordHashRulesTest`).
  `email.feature` i `plaintext-password.feature` USUNIĘTE (+ ich step-defs): format pokrywa `EmailTest`,
  a „required" (pusty/null) to świadomie kontrola warstwy infra — nie testujemy jej tu (patrz pamięć).
  `strong-password-policy.feature` + `StrongPasswordPolicyRules` też USUNIĘTE (pokrycie:
  `CreatePasswordHashRulesTest` + per-constraint testy w password-security-system). Katalog `input/` zniknął.
  REORG: cały test-support scalony w jeden `feature/support/` (6 klas; jeden `InMemoryAuthorizationDataRepository`,
  bez cross-package importów). `RunCucumberTest` posprzątany (martwy `@SelectClasspathResource` out;
  komentarz `@wip` zaktualizowany).
  Zostały 3 feature'y: authenticate / register / refresh-session.
  ✅ ZIELONE: `clean install` BUILD SUCCESS, RunCucumberTest 17 testów / 0 failures.
  (Po drodze: usunięto nieużywaną już zależność `constraint` z security-application pom —
  polityka fail-on-unused ją wyłapała po skasowaniu input-owych feature'ów.)
- ✅ `authenticate.feature` przepisany na biznes (altitude) + jawna polityka brute force.
- ✅ Ustalony kierunek UL: jeden język wszędzie, zero aliasów, kod dostosowuje się do biznesu.
- ✅ `.feature` przepisany na docelowe słownictwo (authenticate/authenticated/rejected/blocked).
- ✅ rename pod jeden język w KODZIE (user, IDE) — potwierdzony (Authenticated/Refreshed/Rejected
  kompilują się). Zostały tylko 3 kosmetyczne `passed` (sekcja niżej), opcjonalne.
- ✅ szew `BlockDurationPolicy` + `AuthenticationFactory` + step-defs + in-memory infra — NAPISANE.
- ✅ ZIELONE: `authenticate.feature` przechodzi w całości (reaktor `clean install`, JDK 25,
  35 testów / 0 failures). `security-system` też (17/0). Moduł odkomentowany w parent.
- ✅ (A) `register.feature` + `refresh-session.feature` otagowane `@wip`; `RunCucumberTest`
  ma `FILTER_TAGS = "not @wip"` → wykluczone z runnera. Po re-runie spodziewane:
  ~27 testów / 0 errors / BUILD SUCCESS (znikają 4 z register + 4 z refresh).
  (Nie zweryfikowane w sandboxie — brak nexusa; user re-runuje `clean install`.)
- ✅ `register.feature` + `refresh-session.feature` PRZEPISANE na biznes/UL (ten sam schemat
   co authenticate): aktor „the user …", wyniki w docelowym języku, plumbing schowany.
   Oba dalej `@wip` (brak step-defs).
   - register: Feature „Registration"; `Valid`→„the user is registered", `Invalid`→
     „registration is rejected" + „email/password flagged as invalid/accepted".
     RULE 3 „already taken" WYCIĘTA (kod jej nie robi — patrz „Ochrona rejestracji").
   - refresh: Feature „Refreshing a session"; PEŁNY poziom „session" (znika „refresh token”,
     też z tytułów reguł active/expired/missing). `Refreshed`→„a fresh session is returned";
     `Expired`→„rejected because the session has expired"; `NotFound`→„rejected because there
     is no session to refresh".
- ✅ REGISTER step-defs ZROBIONE (do odpalenia przez usera):
   - rename `RegisterResult.Valid→Registered`, `Invalid→Rejected` (zrobiony, security-system zielony).
   - DECYZJA B: zły email daje `Rejected` (nie wyjątek). `Register.execute(String,String)` —
     waliduje email (Email.of w try/catch → format error; potem CanRegister policy) i hasło
     (CreatePasswordHash) NIEZALEŻNIE, łączy w `Rejected`. `SecurityService.register` deleguje stringi.
     `RegisterTest` dopasowany. Rule 2 (z „invalid email") działa znów w pełni.
   - glue: `RegisterSteps` (realne polityki: `CanRegister.builder().build()` + `CreatePasswordHash`
     z `PasswordPolicy(MinLength(12), SpecialChars("#?!"))` + Argon2). Reuse `InMemoryUserRepository`
     z authentication.support (ewentualnie wydzielić wspólny support package — drobny cleanup).
   - pom: dodane `email-security-system` (test). `@wip` zdjęty z register.feature.
   - ✅ ZIELONE: `clean install` BUILD SUCCESS, register 4 scenariusze przechodzą.
- ✅ REFRESH step-defs ZROBIONE (do odpalenia): `SessionSteps` + własne in-memory
   `AuthorizationDataRepository` (session.support) wspierające `findRefreshTokenExpirationBy`.
   Stan sesji seedowany przez `RefreshTokenExpiration` (future=active, past=expired, brak=NotFound);
   fixed `Clock`. Bez nowych zależności (refresh nie hashuje). `@wip` zdjęty z refresh-session.feature.
   → po tym buildzie NIE ma już żadnych `@wip` (filtr „not @wip" zostaje na przyszłość, nieszkodliwy).
- ✅ CLEANUP zrobiony: wspólny `feature/support/` (jeden `InMemoryAuthorizationDataRepository`,
   koniec cross-package importów); `RunCucumberTest` posprzątany.
- ⏳ POZOSTAJE: prod DI (BeanFactory + `RandomBlockDurationPolicy`) — infra WIP, osobny wątek.
- 💡 NA PÓŹNIEJ: glosariusz/cross-linking; ochrona rejestracji. (niżej)

## authenticate.feature — podniesienie altitude (business-readability) ✅ ZROBIONE

Plik przepisany na wersję business-readable (zastąpił poprzednią, techniczną).
Decyzja (pkt 4): Gherkin = living documentation dla biznesu; dev/QA czytają Allure,
więc cała precyzja zeszła z `.feature` do step-defs.

Linia podziału (wyostrzona): ukrywamy *plumbing*, NIE *politykę*.
- plumbing (pod maską, do glue): timestampy zegara, IP `192.168.1.1`, `session tokens`,
  `N minutes ago`, format granicy „14 vs 15 min”, komentarze o implementacji/teście.
- polityka (user ją odczuje na własnej skórze → jawna w feature): liczba prób (3),
  okno (15 min), długość bloku (3–10 min). Wrzucona z powrotem jako wykonywalna
  tabela „sign-ins ... are protected by this policy” w Background — jedno źródło prawdy,
  step-def ją czyta (liczba w dokumencie = liczba w teście, zero dryfu). Scenariusze
  odwołują się do niej symbolicznie („reaches the failure limit”), nie powtarzają liczb.
Granice (14 vs 15 min, 4 vs 5 min) stały się „just moments ago” / „long enough ago” /
„the lock period elapses” — konkretne liczby pinujemy w step-defs (pokażą się w Allure).

Próg liczbowy też zniknął z tekstu (pochodzi z ukrytego configu): „3 times” / „third
failure” → „reached the failed sign-in limit” / „stayed under the limit”.
Reguła 6 odchudzona do jednego przykładu (sama wygasalność blokady) — wariant
„still locked during the lock period” był powtórzeniem Reguły 4.
Reguły ponumerowane 1–6.

## Rename pod jeden język (UL) — WSZYSTKO UZGODNIONE, do wykonania (user robi sam)

Zasada: jeden język wszędzie, zero aliasów (słowo biznesowe JEST symbolem); czasownik
zostaje `authenticate`; zamieniaj przypadkowy żargon, ZOSTAW termin niosący znaczenie
(`PlaintextPassword`, „block expiry”).

### Krok 1 — IDE „Rename Symbol” (propaguje do wszystkich użyć, łapie też testy)
Zrób po kolei rename na każdym symbolu (prawy klik → Rename / Shift+F6 w IntelliJ):

| # | symbol (gdzie zdefiniowany) | nowa nazwa |
|---|---|---|
| 1 | `AuthenticationResult.Passed` (security-system …/authentication/AuthenticationResult.java) | `Authenticated` |
| 2 | `AuthenticationResult.Failed` (j.w.) | `Rejected` |
| 3 | `BruteForceProtectionEvent.Passed` (security-domain …/event/BruteForceProtectionEvent.java) | `Allowed` |
| 4 | `AuthenticationEvent.Passed` (security-domain …/event/AuthenticationEvent.java) | `Valid` |
| 5 | `AuthenticationEvent.Failed` (j.w.) | `Invalid` |
| 6 | `RefreshSessionResult.Passed` (security-system …/session/RefreshSessionResult.java) | `Refreshed` |
| 7 | `FailedAuthentication` (security-domain …/entity/) | `RejectedAuthentication` |
| 8 | `FailedAuthenticationDetails` (security-domain …/vo/) | `RejectedAuthenticationDetails` |
| 9 | `FailedAuthenticationId` (security-domain …/vo/) | `RejectedAuthenticationId` |
| 10 | `FailedAuthenticationRepository` (security-domain …/repository/) | `RejectedAuthenticationRepository` |

(`*.Blocked` ZOSTAJE bez zmian we wszystkich typach.)

### Krok 2 — ręcznie (stringi/teksty — Rename ich NIE łapie)
- ✅ `@Label` w `AuthenticationTest.java` — ogarnięte w IDE.
- ⏳ javadoc klasy `RejectedAuthentication`: „…a failed authentication attempt”
  → „…a rejected authentication attempt”. NIE self-linkować (link do samego siebie = bez sensu).
  Formy `{@link RejectedAuthentication rejected authentication}` (tekst po typie = label,
  małe litery ok) używać w INNYCH klasach wspominających pojęcie (Repository, _BruteForceGuard)
  — to pierwszy realny smak cross-linkingu z „pomysłu na później”.
- ⏳ kosmetyka — lokalna zmienna `passed` (rename typu jej nie ruszył), 3 miejsca, `Shift+F6`:
  - `Authentication.java:36/38` `passed` → `valid`
  - `AuthenticationTest.java:102/104` `passed` → `authenticated`
  - `RefreshSessionTest.java:66/68` `passed` → `refreshed`
  - zmienne `blocked` ZOSTAJĄ (Blocked bez zmian).
- ✅ pole `failedAuthenticationRepository` → `rejectedAuthenticationRepository` (typ był `Rejected…`,
  pole zostało stare) — poprawione w 6 plikach (_BruteForceGuard/_Clean/_Update + ich testy).
- ⏳ OTWARTA OŚ (do decyzji): słownictwo „failure/failures” vs „rejection”. Osobne od renamu
  encji. Dotyczy `FailuresCount`, `countFailuresBy`, `hasReachedTheLimit`, oraz @Label/nazw
  metod testów („records_failed_authentication” itd.). „failed to authenticate” / „failed attempts”
  w `.feature` ZOSTAWIĆ (naturalny angielski, nie symbol). Czy ujednolicać resztę na „rejected/rejection”?

### Krok 3 — feature na nowe słownictwo ✅ ZROBIONE
`authenticate.feature` przepisany: Feature „Authentication”, czasownik `authenticate`,
wyniki „is authenticated / authentication is rejected / source is blocked”, „correct credentials”.
Rule 5: zamiast mglistego „long enough ago” → wprost „14 / 15 minutes later” (Twój pomysł;
przy okazji bardziej zgodne z kodem — patrz niżej). Wszystkie 6 reguł zweryfikowane
względem `_BruteForceGuard` (w tym: blok kasuje rekordy porażek; próg okna ścisły >15 min).
UWAGA: rename w KODZIE (Krok 1–2) wciąż do zrobienia — feature już mówi docelowym językiem,
step-defs trzeba będzie spiąć z nazwami po renamie.

### Source (osobny, większy temat — NIE część tego renamu)
Wprowadzić `Source` jako podmiot domeny (guard/`AuthenticationBlock` biorą `Source`,
`IpAddress` znika z sygnatur i staje się polem w `Source`). Source wielopolowy
(np. machineName, browserVersion), ALE rozdzielić role pól:
- TOŻSAMOŚĆ (klucz bloku, w `equals/hashCode`): tylko trudne do podmiany (IP/podsieć/ASN, ew. konto).
- OBSERWOWANE (machineName, browserVersion): forensics/risk, POZA `equals` — inaczej zmiana
  user-agenta = nowa tożsamość = obejście lockoutu (więcej pól = SŁABSZA ochrona).
  Rozważyć osobny `DeviceFingerprint`/`RequestContext`. Uwaga RODO przy utrwalaniu.

## Pomysł na później — glosariusz ubiquitous language (cross-linking)

Marzenie: termin domenowy (np. „authentication") jako hiperłącze w Gherkinie, Allure
i Javadoc — jeden klik → strona terminu z „gdzie używane” (back-references).
Czyli centralny glosariusz UL jako indeks, do którego linkują wszystkie 3 artefakty.

- prawdopodobnie zadanie dla Pythona (static-site generator):
  parsuje `.feature` (oficjalny `gherkin` parser), źródła/Javadoc, `allure-results/*.json`,
  buduje indeks termin→wystąpienia, emituje glosariusz z cross-linkami
- Javadoc: linkowanie wewnątrz Javy jest (`{@link}`/`@see`); link na zewnątrz →
  custom taglet/doclet albo post-processing HTML
- Allure: wspiera linki (`@Link` + szablony w config) → term per test/krok
- Gherkin: brak natywnych linków → własny render `.feature`→HTML albo post-proc raportu
- ŁATWIEJSZE NIŻ SIĘ WYDAWAŁO: po renamie pod jeden język nie ma aliasów —
  termin = symbol, więc „usage” to zwykły grep jednego słowa w `.feature`/`.java`/allure.
  Decyzja o jednym języku jest właśnie warunkiem tego pomysłu.

## Pozostałe otwarte wątki (z tej samej rozmowy)

- **Ochrona rejestracji** — `register(email, password)` nie ma IP/throttlingu/brute force.
  Realna luka (DoS przez haszowanie, masowe fałszywe konta, enumeracja kont przez „email already taken”).
  Osobna kontrola od guarda uwierzytelniania. Do decyzji: czy domykać.
  - DOMKNIĘCIE „already taken": `Register.execute` zapisuje usera BEZ sprawdzenia unikalności;
    `UserAlreadyExistsEvent` istnieje w domenie, ale jest nieużywany. Trzeba: query do repo o duplikat
    + 3. wariant `RegisterResult` (np. `EmailAlreadyTaken`). Wtedy wraca Rule 3 do `register.feature`.

## Implementacja Fazy 2 (step-defs + szew) — ZROBIONE (kod), do odpalenia (user)

Powstałe pliki:
- PROD (security-system, `…system.authentication`):
  - `BlockDurationPolicy` (interfejs, szew na czas bloku)
  - `RandomBlockDurationPolicy` (prod-impl: losuje z config min..max)
  - `AuthenticationFactory` (publiczny montaż; konstruktory zostają package-private)
  - `_BruteForceGuard` — `ThreadLocalRandom` wyjęty, teraz bierze `BlockDurationPolicy`
  - `_BruteForceGuardTest` — konstruktor + asercja deterministyczna (blok = 5 min)
- TEST (security-application, `…feature.authentication[.support]`):
  - `AuthenticationSteps` (glue do wszystkich 6 reguł)
  - `MutableClock`, `FakeHashAlgorithm`, 4× in-memory repo (User, RejectedAuthentication,
    AuthenticationBlock, AuthorizationData)
- `security-application/pom.xml` — dodane `security-config` (test scope; wymóg dependency-analyze)

Weryfikacja w tym środowisku:
- ✅ `security-system` (main + test) kompiluje się na JDK 25 (`C:\j\25`).
- ⚠️ `security-application` NIE skompilowany tu — brak sieci do nexusa + dziury w `.m2`
  (plexus-utils:1.1, junit-platform-suite:1.14.0). To środowiskowe, nie kod.
  Trzeba odpalić w normalnym środowisku z dostępem do repo.

DO ZROBIENIA przez usera:
1. Odpalić testy: `mvn -pl security-application test` (z dostępem do nexusa, JDK 25).
   Moduł jest wykomentowany w parent pom — albo odkomentować `<module>security-application</module>`,
   albo budować bezpośrednio w module.
2. (prod DI) Podpiąć `AuthenticationFactory` + `RandomBlockDurationPolicy` w `BeanFactory`
   (security-infrastructure) — ale infrastructure jest WIP/wyłączone, więc to osobny wątek.
3. (opcjonalnie) 3 kosmetyczne `passed` (sekcja rename wyżej).

Założenia wierne kodowi (zweryfikowane przy pisaniu):
- `the user has reached the failure limit` = 3 rekordy porażek, jeszcze bez bloku;
  blok wpada na kolejnej próbie (guard: `removeAllFor` kasuje rekordy przy bloku).
- okno 15 min: próg ścisły — failure liczona tylko gdy `time > now-15min` (14 → blocked, 15 → ok).
- blok deterministyczny w teście: `BlockDurationPolicy` = stałe 5 min.
