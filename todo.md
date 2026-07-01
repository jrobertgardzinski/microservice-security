# TODO

Tylko otwarte rzeczy. Historia zrobionego = git log.

## Use case'y — inwentarz + backlog (spisane 2026-07-01)

Mamy **5** (warstwa `security-system`): Register, Authenticate (+brute-force guard), RefreshSession
(+reuse-detection), Authorize (walidacja access tokenu), Logout (revoke family). Reuse-detection i
throttling rejestracji to zachowania WEWNĄTRZ Refresh/Register, nie osobne use case'y.

Do dołożenia (~8–10 sensownych), grupowo — mamy lib `email`, więc flow z mailem wykonalne:

Cykl życia konta (największa luka):
- **Verify email** — dziś rejestracja od razu daje aktywne konto; potwierdzać własność. BONUS: kasuje
  enumerację (`/register` → „wysłaliśmy link" zamiast 409). Wysoka wartość, średni wysiłek. [TOP]
- **Reset hasła (forgot password)** — request + complete przez token z maila. Wysoka, średni. [TOP]
- **Change password** (zalogowany, z aktualnym hasłem). Średnia, mały-średni.
- **Change email** (z ponowną weryfikacją). Średnia, średni.
- **Delete/close account** (RODO — prawo do bycia zapomnianym). Średnia, mały-średni.

Zarządzanie sesjami (dobudowuje do fundamentu):
- **Revoke all sessions / „wyloguj wszędzie"** — revoke wszystkich rodzin usera. Wysoka, TANIA (fundament
  jest: revoke-family). [TOP]
- **List active sessions** — user widzi urządzenia/sesje. Średnia, mały-średni. Paruje z powyższym.

Silniejsze uwierzytelnienie (mocny sygnał security):
- **MFA/TOTP: enroll + verify + recovery codes** — duża wartość pokazowa, większy temat.
- **Step-up auth** — ponowne uwierzytelnienie przy wrażliwej akcji. Średni.

Autoryzacja właściwa (jeśli RBAC):
- **Role/permissions: assign + check** — dziś Authorize mówi „token ważny + kto", nie „czy może X".
  Osobny, większy wymiar (model ról).

REKOMENDACJA kolejności (zysk/koszt): 1) Verify email, 2) Reset hasła, 3) Revoke-all-sessions.
MFA jako flagowy „wow" gdy będzie czas.

Powiązane niżej (nie-use-case'owe, ale w temacie): hardening rejestracji (IP/throttling), „Source jako
podmiot domeny", JWT self-contained, UI jako 3. wejście, glosariusz UL.

## Zanim tryb autonomiczny (do decyzji usera)
- **Topologia gita** — `microservice-security` to repo, ale top-level moduły (`adjustable-clock`,
  `infrastructure-micronaut-clock`) i liby (email/password/config) leżą POZA nim → commit ich nie obejmie.
  Zdecydować: **monorepo czy osobne repa per lib**. (jedyne, czego agent nie zgadnie dobrze)
- **Ujednolicić autora commitów** — dryf „Robert Gardziński" vs „jrobertgardzinski". Pod GitHub-a trzeba
  REWRITE historii (mailmap nie wystarcza — GH pokazuje surowego autora), per-repo, kanon
  `Robert Gardziński <jrobertgardzinski@gmail.com>`; force-push. Ustawić `git config --global user.*`.
- **Model pracy** — paczka decyzji-rozwidleń na starcie, potem autonomicznie z **małym zielonym commitem
  per etap** (build+test green przed commitem).

## Pozostałe use-case'y w infra + sterowalny zegar (AKTYWNE)

Dziś infra przepuszcza przez HTTP tylko `register`. `authenticate` i `refreshSession`
istnieją w warstwie application (sterowane wprost), ale nie mają adaptera HTTP ani glue.
Register portował się 1:1, bo jest bezstanowy w czasie; pozostałe zależą od zegara i polityki,
więc najpierw uczynić czas sterowalnym, potem dołożyć adaptery.

Decyzje (zatwierdzone 2026-06-30):
- Zegar: **zamrożony instant**, thread-safe (`AtomicReference`), globalny dla procesu.
  API: `advance(Duration)`, `set(Instant)`, `reset()`, `get()`.
- Dwa moduły: `adjustable-clock` (czysty `java.time`, top-level lib obok email/password/config)
  + `infrastructure-micronaut-clock` (cienki `@Factory` + `TimeControlController`,
  `@Requires(env="test")`, w `main` jarze ale martwy poza profilem test → Angular steruje na deployu).
- `authenticate`: `IpAddress` = X-Forwarded-For **tylko zza trusted proxy**, inaczej remote address.
  Mapowanie: Authenticated→200+tokeny, Rejected→401, Blocked→429 + `Retry-After` z `AuthenticationBlock`.
- `refreshSession`: refresh token w **HttpOnly cookie**; tożsamość wyprowadzona z tokenu (nie osobny email).
  Mapowanie: Refreshed→200+nowe tokeny, Expired→401, NotFound→401 (jednolite, bez leaku).
- HTTP cucumber: kontrakt (status/kanały) zawsze; scenariusze czasowe napędzane przez
  `TimeControlController`. Gdy nie każdy scenariusz pasuje do każdego wejścia → tagi `@http`/`@ui`
  (spójne z sekcją „UI jako 3. wejście").

### Faza 1 — `adjustable-clock` (rdzeń, bez Micronauta) — ZROBIONE (patrz git log)
Moduł `adjustable-clock` (`com.jrobertgardzinski.clock.AdjustableClock`, frozen + `AtomicReference`,
`advance/set/reset`), 8 testów jedn. (w tym współbieżność). `MutableClock` usunięty, `AuthenticationSteps`
przepięty — 19 scenariuszy app-cucumber zielonych. UWAGA: `adjustable-clock` leży poza repo
`microservice-security` (jak email/password/config); jeśli ma być wersjonowany, własne repo + commit.

### Faza 2 — `infrastructure-micronaut-clock` (adapter) — ZROBIONE (patrz git log)
Moduł `infrastructure-micronaut-clock` (`com.jrobertgardzinski.clock.micronaut`): `ClockFactory`
(`@Requires(notEnv=test)`→`systemUTC`; `@Requires(env=test)`→`AdjustableClock` wystawiony
`@Bean(typed={AdjustableClock,Clock})`) + `TimeControlController` `@Controller("/test/clock")`
`@Requires(env=test)` (GET `/`, POST `/advance`|`/set`|`/reset`, ISO-8601). 6 testów: gating
test/prod + napęd przez realny embedded server. Standalone pom (własny import micronaut-platform BOM).
UWAGA do Fazy 3: zweryfikować cross-jar discovery beanów, gdy `security-infrastructure` doda zależność.

### Faza 3 — `authenticate` przez HTTP — ZROBIONE (patrz git log)
`authenticate.feature` przeniesiony do `specs/` (app-runner: dodany `@SelectClasspathResource`).
`AuthenticationController` `POST /authenticate`: 200+tokeny / 401 / 429+`Retry-After`. `ClientIpResolver`
(XFF tylko zza trusted-proxy z `security.trusted-proxies`, inaczej remote addr). `BeanFactory` okablowany
(`Authentication`, wspólny Argon2 hash, brute-force/session config, in-memory repos: rejected/block/authz).
`HttpAuthenticateSteps` napędza feature CZARNĄ SKRZYNKĄ (porażki realnymi POST-ami, czas przez
`/test/clock`) — 10 scenariuszy zielonych; nowy `RunHttpAuthenticateTest` (glue zawężony, register glue też).
Cross-jar discovery beanów zegara potwierdzone. OTWARTE rozwiązane: defaulty `BruteForceConfig` VO
(3/15/3-10) = polityka feature, więc bez config-endpointu; krok `thePolicy` tylko czyta tabelę.
UWAGA: build wymaga `clean` po przeniesieniu feature (stary plik w `target/test-classes` dubluje scenariusze).

### Faza 4 — `refreshSession` przez HTTP — ZROBIONE (patrz git log)
Refactor **token-keyed + hash** (decyzja: token = klucz sesji, nie email). `AuthorizationDataRepository`
przebudowany: `findByRefreshToken`/`deleteByRefreshToken`/`create` (email-keyed metody usunięte).
`RefreshSession` token-only (`SessionRefreshRequest(RefreshToken)`); `RefreshSessionResult.NotFound`
bez emaila. Infra in-memory repo indeksuje po **SHA-256(refresh token)** (nie po surowym tokenie).
`RefreshController` `POST /refresh`: token z **HttpOnly+SameSite=Strict** cookie (`RefreshCookies`,
`Secure` on-by-default, off w `test` przez `application-test.yml`), rotacja na 200, 401 dla Expired/
NotFound/brak-cookie (jednolite). `authenticate` wystawia refresh cookie (token zniknął z body).
`refresh-session.feature` w `specs/`; app-runner czyści `@SelectPackages` (3× `@SelectClasspathResource`).
`HttpRefreshSteps` czarną skrzynką (realny login→cookie, wygaśnięcie przez `/test/clock`) — 3 zielone.
Przepisane: `RefreshSessionTest`, app `SessionSteps`, oba in-memory authz-repo.

## Faza 5 — Persystencja: realne adaptery — ZROBIONE (kod; patrz git log)

Micronaut Data **JDBC** + **Flyway** (V1__init.sql: users/authentication_blocks/rejected_authentications/
sessions). Rekordy-encje `@MappedEntity` w `persistence/` + cienkie adaptery mapujące do domeny (domena
bez adnotacji persystencji). Sesje indeksowane po **SHA-256(refresh token)**, zero surowych tokenów (`sessions`
PK = refresh_token_hash). **Gating po obecności datasource** (lepsze niż env): in-memory repo
`@Requires(missingBeans=DataSource)`, JDBC `@Requires(beans=DataSource)` — `test` bez datasource → in-memory,
`dev`/`prod` z datasource → JDBC. Granica transakcji: `TransactionBoundary` (fasada gated po datasource —
`@Transactional` na kontrolerze nie działa w env bez bazy), kontrolery owijają use-case. `application-dev.yml`/
`-prod.yml`. Refactor token-keyed dociągnięty: `findByRefreshToken` zwraca `StoredSession` (email+refresh expiry),
nie pełne `SessionTokens`. **Testcontainers** `JdbcAdaptersTest` (realny Postgres, Flyway, 4 adaptery) zielone;
`disabledWithoutDocker`. Pełny `clean verify`: BUILD SUCCESS (app 19, infra authenticate 10/register 6/refresh 3/JDBC 4).
Gotcha: po micronaut-data POST bez `consumes` rejestruje trasę dwuznacznie → `/refresh` dostał `consumes=ALL`.

### Deployment — ZROBIONE (zweryfikowane realnym compose up)
Dockerfile przerobiony na **runtime-only** (`eclipse-temurin:25-jre`, uruchamia z classpath
`java -cp 'app.jar:lib/*' App`) — jar+`lib/` budowane na hoście (`copy-dependencies` → target/lib).
To omija problem multi-repo (build w kontenerze nie ma security-libów) i scalanie metadanych Micronauta.
compose: Postgres `16-alpine` z `POSTGRES_PASSWORD`/`POSTGRES_DB=security` + healthcheck, security-service
`MICRONAUT_ENVIRONMENTS=dev` + DB_*; `depends_on: service_healthy`. Zweryfikowane: `compose up` →
Flyway migruje schemat → `POST /register` 201 → wiersz w realnym Postgresie (`flyway_schema_history` v1 ok).
GOTCHA: brak `snakeyaml` na runtime = Micronaut milcząco ignoruje `application*.yml` (datasource!) →
dodany `org.yaml:snakeyaml` (runtime). Uwaga: jar wymaga `mvn package` przed `docker compose build`
(nie self-contained od zera) — jak chcesz build-od-zera, kontekst=security root + maven:3.9-eclipse-temurin-25.

### Dług z Fazy 5 — ZROBIONE
Wyścig dedup w `register`: `JdbcUserRepository.save` łapie `DataAccessException` z SQLSTATE 23505
(unique_violation) → rzuca domenowy `EmailAlreadyTakenException`; `RegistrationAttempt` mapuje go na
`EmailAlreadyTaken`. Constraint = źródło prawdy (collision → 409, nie 500). Test JDBC dedup zielony.

## Faza 6 — Autoryzacja / dostęp do zasobów — ZROBIONE (patrz git log)

Decyzja: access token **opaque + walidacja przez lookup po hashu** (spójne z refresh; JWT odłożony).
`Authorize` (security-system) + `AuthorizationResult` Authorized(Email)/Unauthorized; 3 testy jedn.
`sessions` rozszerzone o `access_token_hash UNIQUE` + `access_token_expiration` (V1); `findByAccessToken`
w porcie + obu adapterach (JDBC po haszu, in-memory skan). `AuthorizationFilter` `@ServerFilter("/me")`
czyta `Authorization: Bearer`, woła `Authorize`, publikuje email jako atrybut → 401 dla brak/zły/wygasły.
Chroniony `GET /me` (`MeController`). `authorize.feature` w `specs/` napędzany czarną skrzynką
(`HttpAuthorizeSteps`: login → token → /me; wygaśnięcie przez `/test/clock`) — 4 scenariusze zielone.
Token hasher uogólniony (`RefreshTokenHashing`→`TokenHashing(AbstractToken)`).

### Logout — ZROBIONE (osobny use-case, jak authorization)
`Logout` (security-system, `execute(RefreshToken)`→`deleteByRefreshToken`, idempotentny) + test jedn.
`LogoutController` `POST /logout`: kasuje sesję po refresh-cookie i czyści cookie (`RefreshCookies.clear()`,
maxAge=0); zawsze 200 (idempotentny bez cookie). Usunięcie wiersza unieważnia OBA tokeny (access hash
był w tym samym wierszu). `logout.feature` w `specs/` (`HttpLogoutSteps`): po logout stary refresh-cookie
→ /refresh 401, stary access token → /me 401, logout-bez-sesji → 200. 3 scenariusze zielone.

### Reuse-detection refresh tokenów — ZROBIONE (patrz git log)
Sesje mają `family` (linię) + `status` (ACTIVE/ROTATED); refresh rotuje (markRotated zamiast delete +
create w tej samej rodzinie). Okazanie ZROTOWANEGO tokenu → `RefreshSessionResult.ReuseDetected` →
`revokeFamily` (cały lineage znika) → 401; nawet świeżo zrotowany token atakującego przestaje działać.
`findByAccessToken` tylko ACTIVE. Logout = revokeFamily. Magazyn: `sessions` + `family_id` + `status`
(V1). Pokrycie: RefreshSessionTest (ReuseDetected), `reuse-detection.feature` HTTP (replay → 401 + nowy
token martwy), JdbcAdaptersTest (rotate/revoke/active-only). `clean verify` zielony.

## Follow-on po Fazie 4 (nowe długi z token-keyed)
- **JWT self-contained** jako alternatywa/uzupełnienie (osobny, większy temat — infra kluczy/podpisów).
- ~~Realna persystencja repo~~ — ZROBIONE w Fazie 5 (JDBC/Postgres, sesje trzymają tylko hashe + metadane).

Powiązane: `Source` jako podmiot domeny (niżej) dotknął wyznaczania IP w fazie 3.

## UI jako 3. wejście (gdy przyjdzie czas)

App-level + HTTP już dowodzą „same behaviour, different entry point" (jeden
`specs/register.feature`, dwa zestawy step-defs). Trzecie wejście = UI (np. Angular +
`cucumber-js` + Playwright) napędza TEN SAM plik z `specs/` po relatywnej ścieżce — dlatego
feature'y leżą w neutralnym `specs/`, a nie w `src/test` któregoś modułu JVM. Gdy spec
urośnie i nie każdy scenariusz pasuje do każdego wejścia → tagi (`@http`, `@ui`) + filtr per
runner. Mały zysk ponad app+infra, więc dopiero po ważniejszych tematach niżej.

## Hardening rejestracji (większy temat)

`register(email, password)` nie ma IP/throttlingu. Luki: DoS przez haszowanie, masowe
fałszywe konta, enumeracja kont. Osobna kontrola od guarda uwierzytelniania. Do decyzji: czy domykać.

## `Source` jako podmiot domeny (większy design)

Guard/`AuthenticationBlock` biorą `Source`, `IpAddress` staje się jego polem. Rozdzielić role:
- TOŻSAMOŚĆ (klucz bloku, `equals/hashCode`): tylko trudne do podmiany (IP/podsieć/ASN/konto).
- OBSERWOWANE (machineName, browserVersion): forensics, POZA `equals` — inaczej zmiana
  user-agenta = obejście lockoutu. Rozważyć `DeviceFingerprint`/`RequestContext`. Uwaga RODO.

## Zaparkowane

- **Glosariusz / cross-linking UL** — termin domenowy jako hiperłącze w Gherkin/Allure/Javadoc
  (Python static-site generator parsujący `.feature` + źródła + allure-results). Za grube na teraz.
